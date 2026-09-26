package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class ExecutionMonitorTest {
    private static final Instant START = Instant.parse("2026-09-19T01:00:00Z");
    private static final ExecutionPollingProperties PROPERTIES = new ExecutionPollingProperties(
            1, Duration.ofSeconds(2), Duration.ofSeconds(40), Duration.ofSeconds(60));

    private final MovableClock clock = new MovableClock(START);
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);
    private final ExecutionWriter executionWriter = mock(ExecutionWriter.class);
    private final ExecutionReservationManager reservationManager = mock(ExecutionReservationManager.class);
    private final ExecutionResultHarvester executionResultHarvester = mock(ExecutionResultHarvester.class);
    private final ExecutionProgressStore store = new ExecutionProgressStore();
    private final RecordingListener listener = new RecordingListener();

    private final ExecutionMonitor sut = new ExecutionMonitor(
            aiRuntimeClient, executionWriter, reservationManager, executionResultHarvester,
            store, PROPERTIES, List.of(listener), clock);

    @Test
    @DisplayName("맡기자마자 접수 상태를 한 번 흘려보낸다 — 엔진을 두드리기 전이다")
    void publishesQueuedStateBeforeTheFirstPoll() {
        sut.begin(execution());

        assertThat(listener.seen).singleElement()
                .satisfies(progress -> assertThat(progress.status()).isEqualTo(ExecutionStatus.QUEUED));
        then(aiRuntimeClient).should(never()).read(anyString());
    }

    @Test
    @DisplayName("엔진이 모르는 값을 줘도 왜 멎었는지는 비우지 않는다")
    void neverLeavesAFailedRunWithoutAReason() {
        given(aiRuntimeClient.read(anyString())).willReturn(unknown());
        sut.begin(execution());

        sut.poll(ExecutionId.of(ID));

        assertThat(listener.seen).last().satisfies(progress -> {
            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure().errorCode()).isEqualTo(ExecutionProgress.UNREPORTED);
            assertThat(progress.failure().fault()).isNull();
        });
    }

    @Test
    @DisplayName("생각지 못한 오류가 나도 사슬을 끊지 않는다 — 기한까지는 계속 물어본다")
    void survivesAnUnexpectedFailure() {
        given(aiRuntimeClient.read(anyString())).willThrow(new IllegalStateException("boom"));
        sut.begin(execution());

        assertThat(sut.poll(ExecutionId.of(ID))).isTrue();
    }

    @Test
    @DisplayName("따라가고 있지 않은 실행은 물어볼 것도 없다")
    void doesNotPollWhatItIsNotWatching() {
        assertThat(sut.poll(ExecutionId.of(ID))).isFalse();

        then(aiRuntimeClient).should(never()).read(anyString());
    }

    @Test
    @DisplayName("값이 달라졌을 때만 민다 — 같은 값을 2초마다 밀면 화면이 헛돈다")
    void pushesOnlyWhatChanged() {
        given(aiRuntimeClient.read(anyString())).willReturn(running(1));
        sut.begin(execution());

        sut.poll(ExecutionId.of(ID));
        sut.poll(ExecutionId.of(ID));

        assertThat(listener.statuses()).containsExactly(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING);
    }

    @Test
    @DisplayName("끝나면 마지막 상태를 밀고 그만 물어보라고 한다")
    void stopsWhenTheRunIsDone() {
        RunStatus finished = succeeded();
        given(aiRuntimeClient.read(anyString())).willReturn(finished);
        Execution started = execution();
        sut.begin(started);

        assertThat(sut.poll(ExecutionId.of(ID))).isFalse();

        assertThat(listener.statuses()).containsExactly(ExecutionStatus.QUEUED, ExecutionStatus.SUCCEEDED);
        assertThat(store.find(ExecutionId.of(ID))).isEmpty();
        then(reservationManager).should().finish(ExecutionId.of(ID), ExecutionStatus.SUCCEEDED, finished);
        then(executionWriter).should(never()).moveTo(ExecutionId.of(ID), ExecutionStatus.SUCCEEDED);
        then(executionResultHarvester).should().harvest(started);
    }

    @Test
    @DisplayName("엔진에 닿지 못해도 기한 전까지는 계속 물어본다")
    void keepsAskingWhileTheEngineIsUnreachable() {
        given(aiRuntimeClient.read(anyString()))
                .willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"));
        sut.begin(execution());

        assertThat(sut.poll(ExecutionId.of(ID))).isTrue();

        assertThat(listener.statuses()).containsExactly(ExecutionStatus.QUEUED);
    }

    @Test
    @DisplayName("단계 수 × 40초 + 60초 동안 변화가 없으면 실패로 끊는다")
    void givesUpOnAStalledRun() {
        given(aiRuntimeClient.read(anyString())).willReturn(running(0));
        sut.begin(execution());

        sut.poll(ExecutionId.of(ID));
        clock.moveTo(START.plus(Duration.ofSeconds(2 * 40 + 60 + 1)));

        assertThat(sut.poll(ExecutionId.of(ID))).isFalse();
        assertThat(listener.seen).last().satisfies(progress -> {
            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure().errorCode()).isEqualTo(ExecutionProgress.STALLED);
            assertThat(progress.failure().fault()).isEqualTo("platform");
        });
        then(reservationManager).should().finish(ExecutionId.of(ID), ExecutionStatus.FAILED, null);
    }

    private static final UUID ID = UUID.fromString("8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13");

    private static Execution execution() {
        return Execution.restore(ExecutionId.of(ID), ExecutionRequestId.of(UUID.randomUUID()),
                WorkflowId.of(UUID.randomUUID()), UserId.of(1L),
                RunId.of("20260919-1"), ExecutionStatus.QUEUED, 2, null, null);
    }

    private static RunStatus running(int done) {
        return new RunStatus("20260919-1", "running",
                new RunStatus.Progress(done, 2, done * 50, "n1", "성적 분석"),
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    private static RunStatus unknown() {
        return new RunStatus("20260919-1", "canceled", null, List.of(), List.of(), List.of(), null, null, false, null);
    }

    private static RunStatus succeeded() {
        return new RunStatus("20260919-1", "succeeded",
                new RunStatus.Progress(2, 2, 100, null, null),
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    private static final class RecordingListener implements ExecutionProgressListener {
        private final List<ExecutionProgress> seen = new ArrayList<>();

        @Override
        public void onProgress(ExecutionProgress progress) {
            seen.add(progress);
        }

        private List<ExecutionStatus> statuses() {
            return seen.stream().map(ExecutionProgress::status).toList();
        }
    }


    private static final class MovableClock extends Clock {
        private Instant now;

        private MovableClock(Instant now) {
            this.now = now;
        }

        private void moveTo(Instant moment) {
            now = moment;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
