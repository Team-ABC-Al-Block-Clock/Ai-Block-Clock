package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionPollingProperties;
import com.aiblockclock.domain.execution.ExecutionRequestId;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.RunId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

class ExecutionProgressSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");
    private static final ExecutionId ID = ExecutionId.of(UUID.randomUUID());
    private static final Execution EXECUTION = Execution.restore(ID, ExecutionRequestId.of(UUID.randomUUID()),
            WorkflowId.of(UUID.randomUUID()), UserId.of(1L), RunId.of("20260919-1"), ExecutionStatus.QUEUED, 2, null, false, null);
    private static final ExecutionPollingProperties PROPERTIES = new ExecutionPollingProperties(
            1, Duration.ofSeconds(2), Duration.ofSeconds(40), Duration.ofSeconds(60));

    private final ManualScheduler scheduler = new ManualScheduler();
    private final ExecutionService executionService = mock(ExecutionService.class);

    private final ExecutionProgressScheduler sut = new ExecutionProgressScheduler(
            scheduler, executionService, PROPERTIES, Clock.fixed(NOW, ZoneId.of("UTC")));

    @Test
    @DisplayName("따라가기 시작하면 간격만큼 뒤에 첫 조회를 잡는다")
    void schedulesTheFirstAskAfterTheInterval() {
        sut.follow(EXECUTION);

        then(executionService).should().beginWatching(EXECUTION);
        assertThat(scheduler.pending()).isEqualTo(1);
        assertThat(scheduler.lastPlannedFor()).isEqualTo(NOW.plusSeconds(2));
    }

    @Test
    @DisplayName("더 볼 것이 있다고 하면 다음 조회를 잡는다")
    void asksAgainWhileTheServiceSaysSo() {
        given(executionService.pollOnce(ID)).willReturn(true);
        sut.follow(EXECUTION);

        scheduler.runNext();

        assertThat(scheduler.pending()).isEqualTo(1);
    }

    @Test
    @DisplayName("그만하라고 하면 더 잡지 않는다")
    void stopsWhenTheServiceSaysSo() {
        given(executionService.pollOnce(ID)).willReturn(false);
        sut.follow(EXECUTION);

        scheduler.runNext();

        assertThat(scheduler.pending()).isZero();
    }

    private static final class ManualScheduler implements TaskScheduler {
        private final Deque<Runnable> queued = new ArrayDeque<>();
        private Instant lastPlannedFor;

        private int pending() {
            return queued.size();
        }

        private Instant lastPlannedFor() {
            return lastPlannedFor;
        }

        private void runNext() {
            queued.poll().run();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            queued.add(task);
            lastPlannedFor = startTime;

            return null;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }
    }
}
