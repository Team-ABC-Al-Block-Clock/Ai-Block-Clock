package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

// 실행을 2초 폴링 대신 이벤트로 따라가고, 이벤트가 끊긴 실행만 엔진에 묻는다(ADR-0001).
// 단계 둘인 실행이라 감시 기한은 2 × 60초(per-step) + 60초(grace) = 180초다. 큐 대기 한도는 10분이다.
// 큐 대기 안전망의 유예는 5분이라, 넘긴 채 QUEUED 인 실행은 넘긴 지 15분이 되면 닫는다.
class ExecutionTrackerTest {
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final ExecutionId ID = ExecutionId.of(UUID.fromString("6f1c2a9e-0000-4000-8000-000000000001"));
    private static final String RUN_ID = ID.value().toString();
    private static final String OWNER = "7001";
    private static final Duration DEADLINE = Duration.ofSeconds(180);
    private static final Duration QUEUE_LIMIT_AND_GRACE = Duration.ofMinutes(10).plusSeconds(60);
    private static final Duration QUEUE_GRACE = Duration.ofMinutes(5);
    private static final Duration QUEUE_LIMIT_AND_QUEUE_GRACE = Duration.ofMinutes(15);

    private final MovableClock clock = new MovableClock(NOW);
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);
    private final RunQueuePublisher runQueuePublisher = mock(RunQueuePublisher.class);
    private final ExecutionReader executionReader = mock(ExecutionReader.class);
    private final ExecutionWriter executionWriter = mock(ExecutionWriter.class);
    private final ExecutionReservationManager reservationManager = mock(ExecutionReservationManager.class);
    private final ExecutionResultCollector resultCollector = mock(ExecutionResultCollector.class);
    private final ExecutionProgressStore store = new ExecutionProgressStore();
    private final RecordingListener listener = new RecordingListener();

    private final ExecutionTracker sut = trackerWithQueueGrace(QUEUE_GRACE);

    @Nested
    class 실행_이벤트를_적용한다 {

        @Test
        void accepted_가_오면_RUNNING_으로_옮기고_단계_수를_total_로_구독자에게_보낸다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            then(executionWriter).should().moveTo(ID, ExecutionStatus.RUNNING);
            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.id()).isEqualTo(ID);
                assertThat(progress.status()).isEqualTo(ExecutionStatus.RUNNING);
                assertThat(progress.total()).isEqualTo(2);
            });
            assertThat(store.find(ID)).hasValueSatisfying(progress ->
                    assertThat(progress.status()).isEqualTo(ExecutionStatus.RUNNING));
        }

        // accepted 의 snapshot 은 아직 queued 다. 상태는 snapshot.status 가 아니라 type 으로 정한다.
        @Test
        void 상태는_snapshot_이_아니라_이벤트_type_으로_정한다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            assertThat(listener.statuses()).containsExactly(ExecutionStatus.RUNNING);
            then(executionWriter).should(never()).moveTo(ID, ExecutionStatus.QUEUED);
        }

        @Test
        void accepted_가_오면_감시를_시작한다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            assertThat(sut.isWatching(ID)).isTrue();
        }

        @Test
        void progress_가_오면_snapshot_의_진행_상황을_저장하고_구독자에게_보낸다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));

            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.RUNNING);
                assertThat(progress.done()).isEqualTo(1);
                assertThat(progress.total()).isEqualTo(2);
                assertThat(progress.percent()).isEqualTo(50);
                assertThat(progress.currentStepName()).isEqualTo("가정통신문 작성");
                assertThat(progress.steps()).containsExactly(
                        new ExecutionProgress.Step("성적 분석", "succeeded"),
                        new ExecutionProgress.Step("가정통신문 작성", "running"));
            });
            assertThat(store.find(ID)).contains(listener.last());
        }

        @Test
        void 이미_RUNNING_인_실행에_progress_가_와도_상태를_다시_쓰지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));

            then(executionWriter).should(never()).moveTo(any(), any());
        }

        @Test
        void accepted_보다_progress_가_먼저_와도_RUNNING_으로_옮긴다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));

            then(executionWriter).should().moveTo(ID, ExecutionStatus.RUNNING);
        }

        @Test
        void 이벤트가_이어_와도_RUNNING_으로_옮기는_쓰기는_한_번이다() {
            given(executionReader.read(ID))
                    .willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 3, OWNER));

            then(executionWriter).should(times(1)).moveTo(ID, ExecutionStatus.RUNNING);
            assertThat(listener.seen).hasSize(3);
        }

        @Test
        void succeeded_가_오면_SUCCEEDED_로_닫고_snapshot_원문을_근거로_넘긴다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            RunEvent succeeded = RunEventFixtures.succeeded(RUN_ID, 1, 4, OWNER);

            sut.apply(succeeded);

            then(reservationManager).should().finish(ID, ExecutionStatus.SUCCEEDED,
                    EngineReport.reported(succeeded.snapshot(), succeeded.snapshotJson()));
        }

        @Test
        void succeeded_로_닫은_뒤에_결과_볼륨에서_결과물을_가져온다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));

            sut.apply(RunEventFixtures.succeeded(RUN_ID, 1, 4, OWNER));

            InOrder order = inOrder(reservationManager, resultCollector);
            order.verify(reservationManager).finish(eq(ID), eq(ExecutionStatus.SUCCEEDED), any(EngineReport.class));
            order.verify(resultCollector).collect(ID);
        }

        @Test
        void succeeded_로_닫으면_구독자에게_SUCCEEDED_를_보내고_감시와_진행_저장소에서_뺀다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            sut.apply(RunEventFixtures.succeeded(RUN_ID, 1, 2, OWNER));

            assertThat(listener.last().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(listener.last().percent()).isEqualTo(100);
            assertThat(sut.isWatching(ID)).isFalse();
            assertThat(store.find(ID)).isEmpty();
        }

        @Test
        void failed_가_오면_FAILED_로_닫고_snapshot_의_failure_로_판정하게_넘긴다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            RunEvent failed = RunEventFixtures.failed(RUN_ID, 1, 4, OWNER);

            sut.apply(failed);

            then(reservationManager).should().finish(ID, ExecutionStatus.FAILED,
                    EngineReport.reported(failed.snapshot(), failed.snapshotJson()));
            then(resultCollector).should(never()).collect(any());
            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.failure()).isEqualTo(
                        new ExecutionProgress.Failure("가정통신문 작성", "MODEL_TIMEOUT", "supplier"));
            });
            assertThat(sut.isWatching(ID)).isFalse();
            assertThat(store.find(ID)).isEmpty();
        }

        @Test
        void rejected_도_failed_처럼_FAILED_로_닫는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));
            RunEvent rejected = RunEventFixtures.rejected(RUN_ID, 1, 1);

            sut.apply(rejected);

            then(reservationManager).should().finish(ID, ExecutionStatus.FAILED,
                    EngineReport.reported(rejected.snapshot(), rejected.snapshotJson()));
            assertThat(listener.last().status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(listener.last().failure().errorCode()).isEqualTo("INVALID_WORKFLOW");
            assertThat(listener.last().failure().fault()).isEqualTo("consumer");
        }

        @Test
        void 까닭이_없는_rejected_는_알리지_않은_실패로_플랫폼_탓이다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.rejectedWithoutFailure(RUN_ID, 1, 1));

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(ExecutionFailure.judge(report.getValue())).isEqualTo(new ExecutionFailure(
                    null, "ABC_RUNTIME_UNREPORTED", ExecutionFault.PLATFORM, false, null, null));
            assertThat(listener.last().failure().errorCode()).isEqualTo("ABC_RUNTIME_UNREPORTED");
        }

        // 정산 근거(execution_run_record)는 닫힌 뒤 근거 스레드가 적는다. 원문을 넘겼으니 거기서도 엔진에 묻지 않는다.
        @Test
        void 이벤트를_적용하는_동안_엔진_HTTP_를_부르지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));
            sut.apply(RunEventFixtures.succeeded(RUN_ID, 1, 3, OWNER));

            then(aiRuntimeClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    class 순서가_어긋난_이벤트는_버린다 {

        @Test
        void 이미_적용한_것과_같은_순번의_이벤트는_버린다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));
            ExecutionProgress applied = store.find(ID).orElseThrow();

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 2));

            assertThat(listener.seen).hasSize(1);
            assertThat(store.find(ID)).contains(applied);
        }

        @Test
        void 순번이_거꾸로_온_이벤트는_버린다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 3, OWNER));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 2));

            assertThat(listener.seen).hasSize(1);
            assertThat(store.find(ID).orElseThrow().done()).isEqualTo(1);
        }

        @Test
        void 바로_다음_순번은_적용한다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 2));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 3, OWNER));

            assertThat(listener.seen).hasSize(2);
            assertThat(store.find(ID).orElseThrow().done()).isEqualTo(1);
        }

        // 런타임이 죽어 다시 배달된 실행은 attempt 가 오르고 seq 가 1 부터 다시 센다.
        @Test
        void attempt_가_크면_seq_가_작아도_적용한다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 5, OWNER));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 2, 1));

            assertThat(listener.seen).hasSize(2);
            assertThat(store.find(ID).orElseThrow().done()).isZero();
        }

        @Test
        void attempt_가_작으면_seq_가_커도_버린다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 2, 1));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 9, OWNER));

            assertThat(listener.seen).hasSize(1);
            assertThat(store.find(ID).orElseThrow().done()).isZero();
        }

        @Test
        void 버린_완료_이벤트로는_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 3, OWNER));

            sut.apply(RunEventFixtures.succeeded(RUN_ID, 1, 3, OWNER));

            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            then(resultCollector).shouldHaveNoInteractions();
        }

        @Test
        void 순번은_실행마다_따로_센다() {
            ExecutionId other = ExecutionId.of(UUID.fromString("7a2d3b0f-0000-4000-8000-000000000002"));
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            given(executionReader.read(other)).willReturn(execution(other, ExecutionStatus.RUNNING, 2));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 5, OWNER));

            sut.apply(RunEventFixtures.progress(other.value().toString(), 1, 2, OWNER));

            assertThat(store.find(other)).isPresent();
        }
    }

    @Nested
    class 끝났거나_모르는_실행 {

        @Test
        void 이미_끝난_실행에_온_이벤트는_아무것도_바꾸지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.SUCCEEDED));

            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));

            then(executionWriter).shouldHaveNoInteractions();
            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
            assertThat(store.find(ID)).isEmpty();
            assertThat(sut.isWatching(ID)).isFalse();
        }

        @Test
        void 이미_끝난_실행에_온_완료_이벤트로_다시_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.FAILED));

            sut.apply(RunEventFixtures.succeeded(RUN_ID, 2, 4, OWNER));

            then(reservationManager).shouldHaveNoInteractions();
            then(resultCollector).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        @Test
        void 모르는_실행의_이벤트는_던지지_않고_넘어간다() {
            given(executionReader.read(ID)).willThrow(new CoreException(ErrorType.EXECUTION_NOT_FOUND));

            assertThatCode(() -> sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER)))
                    .doesNotThrowAnyException();

            assertThat(listener.seen).isEmpty();
            then(reservationManager).shouldHaveNoInteractions();
        }

        // 우리 실행 번호는 UUID 다. UUID 가 아닌 번호는 우리가 모르는 실행이다(B21) — 다시 시도해도 알게 되지 않는다.
        @Test
        void 실행_번호가_UUID_가_아닌_이벤트는_모르는_실행으로_보고_던지지_않고_아무것도_바꾸지_않는다() {
            assertThatCode(() -> sut.apply(RunEventFixtures.progress("not-a-uuid", 1, 2, OWNER)))
                    .doesNotThrowAnyException();

            then(executionWriter).shouldHaveNoInteractions();
            then(reservationManager).shouldHaveNoInteractions();
            then(resultCollector).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // confirm 기록보다 이벤트가 먼저 왔다. 넘긴 기록을 먼저 남겨야 감시 · 근거가 실행 번호로 엔진을 찾는다.
        @Test
        void runId_가_비어_있는_실행에_이벤트가_먼저_오면_넘긴_기록을_먼저_남기고_적용한다() {
            given(executionReader.read(ID)).willReturn(undispatched());

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            InOrder order = inOrder(reservationManager, executionWriter);
            order.verify(reservationManager).markDispatched(ID, NOW);
            order.verify(executionWriter).moveTo(ID, ExecutionStatus.RUNNING);
        }

        @Test
        void 이미_넘긴_실행이면_넘긴_기록을_다시_남기지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            then(reservationManager).should(never()).markDispatched(any(), any());
        }
    }

    @Nested
    class 처리_중_오류 {

        // 리스너가 다시 시도하게 하려면 삼키지 말고 던져야 한다.
        @Test
        void DB_오류는_밖으로_던진다() {
            DataAccessResourceFailureException down = new DataAccessResourceFailureException("db down");
            given(executionReader.read(ID)).willThrow(down);

            assertThatThrownBy(() -> sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER))).isSameAs(down);
        }

        // 적용하지 못한 이벤트의 순번을 적용한 것으로 남기면, 다시 온 같은 이벤트를 버려 실행이 영영 닫히지 않는다.
        @Test
        void 처리하다_실패한_이벤트가_다시_오면_버리지_않고_적용한다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            RunEvent succeeded = RunEventFixtures.succeeded(RUN_ID, 1, 4, OWNER);
            willThrow(new DataAccessResourceFailureException("db down")).willDoNothing()
                    .given(reservationManager).finish(eq(ID), eq(ExecutionStatus.SUCCEEDED), any(EngineReport.class));

            assertThatThrownBy(() -> sut.apply(succeeded)).isInstanceOf(DataAccessResourceFailureException.class);
            sut.apply(succeeded);

            then(reservationManager).should(times(2))
                    .finish(eq(ID), eq(ExecutionStatus.SUCCEEDED), any(EngineReport.class));
            assertThat(listener.last().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
        }
    }

    @Nested
    class 감시 {

        @Test
        void 마지막_이벤트에서_기한_전이면_점검해도_엔진에_묻지_않는다() {
            accept();
            clock.moveTo(NOW.plus(DEADLINE).minusMillis(1));

            sut.check();
            sut.check();

            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 마지막_이벤트에서_기한이_되면_엔진에_한_번_묻는다() {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("succeeded"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(aiRuntimeClient).should(times(1)).read(RUN_ID);
        }

        @Test
        void 기한은_단계_수에_비례한다() {
            given(executionReader.read(ID)).willReturn(execution(ID, ExecutionStatus.QUEUED, 3),
                    execution(ID, ExecutionStatus.RUNNING, 3));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW));
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("succeeded"));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));

            clock.moveTo(NOW.plusSeconds(3 * 60 + 60).minusMillis(1));
            sut.check();
            then(aiRuntimeClient).should(never()).read(anyString());

            clock.moveTo(NOW.plusSeconds(3 * 60 + 60));
            sut.check();
            then(aiRuntimeClient).should(times(1)).read(RUN_ID);
        }

        @Test
        void progress_가_오면_기한을_그_이벤트부터_다시_잰다() {
            accept();
            clock.moveTo(NOW.plusSeconds(100));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("succeeded"));

            clock.moveTo(NOW.plus(DEADLINE));
            sut.check();
            then(aiRuntimeClient).should(never()).read(anyString());

            clock.moveTo(NOW.plusSeconds(100).plus(DEADLINE));
            sut.check();
            then(aiRuntimeClient).should(times(1)).read(RUN_ID);
        }

        @Test
        void 기한이_지나_엔진이_성공했다고_하면_그_응답으로_닫고_결과물을_가져온다() {
            accept();
            RunStatus finished = status("succeeded");
            given(aiRuntimeClient.read(RUN_ID)).willReturn(finished);
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.SUCCEEDED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.ENGINE);
            assertThat(report.getValue().run()).isEqualTo(finished);
            then(resultCollector).should().collect(ID);
            assertThat(listener.last().status()).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(sut.isWatching(ID)).isFalse();
        }

        @Test
        void 기한이_지나_엔진이_실패했다고_하면_그_응답으로_FAILED_로_닫는다() {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("failed"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.ENGINE);
            then(resultCollector).should(never()).collect(any());
            then(runQueuePublisher).should(never()).cancel(anyString());
            assertThat(sut.isWatching(ID)).isFalse();
        }

        @ParameterizedTest
        @CsvSource({"running", "queued"})
        void 기한이_지나도_엔진이_아직_돌거나_기다린다고_하면_취소를_걸고_STALLED_로_닫는다(String engineStatus) {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status(engineStatus));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(runQueuePublisher).should().cancel(RUN_ID);
            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.STALLED);
            assertThat(listener.last().failure().errorCode()).isEqualTo("ABC_RUNTIME_STALLED");
            assertThat(listener.last().failure().fault()).isEqualTo("platform");
            assertThat(sut.isWatching(ID)).isFalse();
        }

        // 어디서 멎었는지는 엔진이 지금 답한 것이 아니라 우리가 마지막으로 받은 진행 상황에서 가져온다.
        @Test
        void STALLED_로_닫으면_구독자에게_마지막으로_받은_진행_상황의_단계에서_멎었다고_알린다() {
            accept();
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("running"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.done()).isEqualTo(1);
                assertThat(progress.steps()).hasSize(2);
                assertThat(progress.failure()).isEqualTo(new ExecutionProgress.Failure(
                        "가정통신문 작성", "ABC_RUNTIME_STALLED", "platform"));
            });
        }

        // 재기동 뒤 다시 붙인 실행이라 받은 진행 상황이 없다. 단계 수로 total 을 채운다.
        @Test
        void 이벤트_없이_다시_붙인_실행을_STALLED_로_닫으면_단계_수를_total_로_알린다() {
            assertThat(sut.begin(execution(ExecutionStatus.RUNNING))).isTrue();
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("running"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.done()).isZero();
                assertThat(progress.total()).isEqualTo(2);
                assertThat(progress.steps()).isEmpty();
                assertThat(progress.failure()).isEqualTo(new ExecutionProgress.Failure(
                        null, "ABC_RUNTIME_STALLED", "platform"));
            });
        }

        @Test
        void 취소를_걸지_못해도_STALLED_로_닫는다() {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("running"));
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker down"))
                    .given(runQueuePublisher).cancel(RUN_ID);
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
        }

        // 런타임이 죽어도 메시지는 다시 배달된다. 404 는 "영영 사라짐" 이 아니라 아직 다시 받지 않은 것일 수 있다.
        @Test
        void 엔진이_실행을_모르는데_넘긴_지_큐_대기_한도와_grace_가_지나지_않았으면_닫지_않고_기한을_새로_건다() {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            assertThat(sut.isWatching(ID)).isTrue();

            clock.moveTo(NOW.plus(DEADLINE).plus(DEADLINE).minusMillis(1));
            sut.check();
            then(aiRuntimeClient).should(times(1)).read(RUN_ID);

            clock.moveTo(NOW.plus(DEADLINE).plus(DEADLINE));
            sut.check();
            then(aiRuntimeClient).should(times(2)).read(RUN_ID);
        }

        @Test
        void 엔진이_실행을_모르고_넘긴_지_큐_대기_한도와_grace_가_꼭_지났으면_LOST_로_닫는다() {
            acceptDispatchedAt(NOW.plus(DEADLINE).minus(QUEUE_LIMIT_AND_GRACE));
            given(aiRuntimeClient.read(RUN_ID)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.LOST);
            assertThat(listener.last().failure().errorCode()).isEqualTo("ABC_RUNTIME_LOST");
            then(runQueuePublisher).should(never()).cancel(anyString());
            assertThat(sut.isWatching(ID)).isFalse();
        }

        @Test
        void 엔진이_실행을_몰라도_넘긴_지_큐_대기_한도와_grace_에서_1ms_모자라면_닫지_않는다() {
            acceptDispatchedAt(NOW.plus(DEADLINE).minus(QUEUE_LIMIT_AND_GRACE).plusMillis(1));
            given(aiRuntimeClient.read(RUN_ID)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            assertThat(sut.isWatching(ID)).isTrue();
        }

        @Test
        void 엔진에_닿지_못해도_큐_대기_한도와_grace_안이면_닫지_않는다() {
            accept();
            given(aiRuntimeClient.read(RUN_ID)).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            then(runQueuePublisher).should(never()).cancel(anyString());
            assertThat(sut.isWatching(ID)).isTrue();
        }

        @Test
        void 엔진에_닿지_못한_채_큐_대기_한도와_grace_가_지났으면_취소를_걸고_STALLED_로_닫는다() {
            acceptDispatchedAt(NOW.minus(Duration.ofHours(1)));
            given(aiRuntimeClient.read(RUN_ID)).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));
            clock.moveTo(NOW.plus(DEADLINE));

            sut.check();

            then(runQueuePublisher).should().cancel(RUN_ID);
            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.STALLED);
        }

        // 아직 accepted 전인 실행은 큐에서 기다리는 중이다. 대기는 TTL 과 dead letter 가 맡는다.
        @Test
        void QUEUED_실행은_감시에_붙지_않아_엔진에_묻지도_닫지도_않는다() {
            assertThat(sut.begin(execution(ExecutionStatus.QUEUED))).isFalse();

            clock.moveTo(NOW.plus(Duration.ofDays(1)));
            sut.check();

            assertThat(sut.isWatching(ID)).isFalse();
            then(aiRuntimeClient).shouldHaveNoInteractions();
            then(reservationManager).shouldHaveNoInteractions();
        }

        @Test
        void 복구가_다시_붙인_RUNNING_실행은_붙인_시각부터_기한을_잰다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW));
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("succeeded"));

            assertThat(sut.begin(execution(ExecutionStatus.RUNNING))).isTrue();

            clock.moveTo(NOW.plus(DEADLINE).minusMillis(1));
            sut.check();
            then(aiRuntimeClient).should(never()).read(anyString());

            clock.moveTo(NOW.plus(DEADLINE));
            sut.check();
            then(aiRuntimeClient).should().read(RUN_ID);
        }

        @Test
        void 이미_감시_중인_실행을_다시_붙여도_기한을_새로_잡지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW));
            given(aiRuntimeClient.read(RUN_ID)).willReturn(status("succeeded"));
            sut.begin(execution(ExecutionStatus.RUNNING));
            clock.moveTo(NOW.plusSeconds(100));

            assertThat(sut.begin(execution(ExecutionStatus.RUNNING))).isFalse();

            clock.moveTo(NOW.plus(DEADLINE));
            sut.check();
            then(aiRuntimeClient).should().read(RUN_ID);
        }
    }

    @Nested
    class dead_letter {

        @ParameterizedTest
        @CsvSource({
                "EXPIRED, ABC_QUEUE_EXPIRED",
                "DELIVERY_LIMIT, ABC_RUNTIME_RETRY_EXHAUSTED",
                "OTHER, ABC_RUNTIME_DEAD_LETTERED"})
        void dead_letter_로_온_실행은_사유의_코드로_플랫폼_탓_실패로_닫는다(DeadLetterReason reason, String code) {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.closeDeadLettered(ID, reason);

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.DEAD_LETTERED);
            assertThat(ExecutionFailure.judge(report.getValue())).satisfies(failure -> {
                assertThat(failure.errorCode()).isEqualTo(code);
                assertThat(failure.fault()).isEqualTo(ExecutionFault.PLATFORM);
                assertThat(failure.supplier()).isNull();
                assertThat(failure.affectsSuccessRate()).isFalse();
            });
            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.failure().errorCode()).isEqualTo(code);
                assertThat(progress.failure().fault()).isEqualTo("platform");
            });
        }

        @Test
        void 받은_snapshot_이_없으면_run_없이_닫는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));

            sut.closeDeadLettered(ID, DeadLetterReason.EXPIRED);

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().run()).isNull();
        }

        // 성공한 단계가 있으면 그 공급자 몫은 플랫폼이 낸다. 그 판단은 마지막으로 받은 snapshot 으로 한다.
        @Test
        void 재배달_한도를_넘긴_실행은_마지막으로_받은_snapshot_으로_성공한_단계를_판단하게_넘긴다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));
            RunEvent progress = RunEventFixtures.progress(RUN_ID, 1, 2, OWNER);
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
            sut.apply(progress);

            sut.closeDeadLettered(ID, DeadLetterReason.DELIVERY_LIMIT);

            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
            then(reservationManager).should().finish(eq(ID), eq(ExecutionStatus.FAILED), report.capture());
            assertThat(report.getValue().run()).isEqualTo(progress.snapshot());
            assertThat(sut.isWatching(ID)).isFalse();
            assertThat(store.find(ID)).isEmpty();
        }

        // 대기 화면이 끝나도록 구독자에게 알린다. 어디까지 돌았는지는 마지막으로 받은 진행 상황에서 가져온다.
        @Test
        void dead_letter_로_닫으면_구독자에게_마지막_진행_상황에서_멎은_단계와_함께_알린다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
            sut.apply(RunEventFixtures.progress(RUN_ID, 1, 2, OWNER));

            sut.closeDeadLettered(ID, DeadLetterReason.DELIVERY_LIMIT);

            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.done()).isEqualTo(1);
                assertThat(progress.total()).isEqualTo(2);
                assertThat(progress.steps()).containsExactly(
                        new ExecutionProgress.Step("성적 분석", "succeeded"),
                        new ExecutionProgress.Step("가정통신문 작성", "running"));
                assertThat(progress.failure()).isEqualTo(new ExecutionProgress.Failure(
                        "가정통신문 작성", "ABC_RUNTIME_RETRY_EXHAUSTED", "platform"));
            });
        }

        @Test
        void 이미_끝난_실행의_dead_letter_는_아무것도_하지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.SUCCEEDED));

            sut.closeDeadLettered(ID, DeadLetterReason.EXPIRED);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }
    }

    // 넘긴 채 런타임이 받지 않은 실행을 dead letter 가 닫지 못하고 남긴 경우다(ADR-0001 후속 #21).
    // 복구 스케줄러가 넘긴 채 QUEUED 인 실행마다 부른다. 넘긴 시각(execution_outbox.dispatched_at)에서
    // 큐 대기 한도(10분) + 유예(5분)가 지났으면 dead letter 를 잃은 것으로 보고 닫는다.
    // 닫기는 행을 잠근 뒤에도 QUEUED 일 때만 닫는 finishQueued 로 한다(B9b). 닫혔는지는 그 답(true · false)으로 안다.
    @Nested
    class 큐_대기_안전망 {

        // B6 — 런타임이 받은 적이 없어 마지막으로 본 상태가 없다. 그래서 정산할 단계가 없고 예치는 모두 돌아간다.
        @Test
        void 넘긴_지_큐_대기_한도와_유예가_지난_QUEUED_실행은_ABC_QUEUE_EXPIRED_플랫폼_탓_실패로_닫는다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE).minusSeconds(1));

            sut.closeOverdueInQueue(ID);

            EngineReport report = closedReport();
            assertThat(report.run()).isNull();
            assertThat(ExecutionFailure.judge(report)).satisfies(failure -> {
                assertThat(failure.errorCode()).isEqualTo("ABC_QUEUE_EXPIRED");
                assertThat(failure.fault()).isEqualTo(ExecutionFault.PLATFORM);
                assertThat(failure.supplier()).isNull();
                assertThat(failure.affectsSuccessRate()).isFalse();
            });
        }

        // B7 — 감시가 큐 대기 한도를 재는 것(지금이 그 시각보다 앞서지 않으면 지났다)과 같은 해석이다.
        @Test
        void 넘긴_지_꼭_15분이면_닫는다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE));

            sut.closeOverdueInQueue(ID);

            then(reservationManager).should()
                    .finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
        }

        @Test
        void 넘긴_지_15분에서_1ms_모자라면_닫지_않는다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE).plusMillis(1));

            sut.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // B8 — 런타임이 받았다(accepted). 이벤트가 끊기면 감시가 닫는다.
        @Test
        void RUNNING_실행은_오래됐어도_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.RUNNING));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

            sut.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // B8 — 넘긴 기록이 없는 실행은 디스패처가 다시 발행하거나 한도가 지나면 버린다.
        @Test
        void 실행_번호가_없는_실행은_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(undispatched());
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

            sut.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // B8 — 넘긴 시각을 모르면 큐에서 얼마나 기다렸는지 모른다. 감시(LOST)와 달리 지난 것으로 보지 않는다.
        @Test
        void 넘긴_시각이_없는_실행은_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.empty());

            sut.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // B8
        @Test
        void 이미_끝난_실행은_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.SUCCEEDED));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));

            sut.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
            assertThat(listener.seen).isEmpty();
        }

        // B9 — 복구 스케줄러가 읽은 뒤 그 사이 dead letter 가 와서 닫았다. 다시 닫지도 알리지도 않는다.
        @Test
        void dead_letter_경로가_먼저_닫은_실행을_안전망이_다시_보면_아무것도_하지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.FAILED));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));
            sut.closeDeadLettered(ID, DeadLetterReason.EXPIRED);

            sut.closeOverdueInQueue(ID);

            then(reservationManager).should(times(1))
                    .finish(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
            then(reservationManager).should(never()).finishQueued(any(), any(), any());
            assertThat(listener.seen).hasSize(1);
        }

        // B9 — 반대로 안전망이 먼저 닫은 뒤에 dead letter 가 늦게 와도 다시 닫지 않는다.
        @Test
        void 안전망이_먼저_닫은_실행에_dead_letter_가_늦게_와도_다시_닫지_않는다() {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.FAILED));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(NOW.minus(Duration.ofHours(1))));
            given(reservationManager.finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class)))
                    .willReturn(true);
            sut.closeOverdueInQueue(ID);

            sut.closeDeadLettered(ID, DeadLetterReason.EXPIRED);

            then(reservationManager).should(times(1))
                    .finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            assertThat(listener.seen).hasSize(1);
        }

        // B9b — 다시 읽은 뒤(QUEUED) 닫기 전에 accepted 가 와서 RUNNING 이 됐다. 잠근 뒤 보니 QUEUED 가 아니라 닫히지 않았다.
        // 실패를 알리지도, 감시와 진행 저장소에서 빼지도 않고, 조건 없이 닫는 finish 로 넘어가지도 않는다. 감시가 맡는다.
        @Test
        void 잠근_뒤_QUEUED_가_아니라_닫히지_않았으면_알리지도_감시에서_빼지도_않는다() {
            queuedDispatchedAt(NOW.minus(Duration.ofHours(1)));
            sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
            given(reservationManager.finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class)))
                    .willReturn(false);

            sut.closeOverdueInQueue(ID);

            then(reservationManager).should()
                    .finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
            then(reservationManager).should(never()).finish(any(), any(), any(EngineReport.class));
            assertThat(listener.statuses()).containsExactly(ExecutionStatus.RUNNING);
            assertThat(sut.isWatching(ID)).isTrue();
            assertThat(store.find(ID)).isPresent();
        }

        // B10 — 근거(execution_run_record.closed_by)에 브로커가 dead letter 로 닫은 실행과 다른 값으로 남는다.
        @Test
        void 안전망이_닫은_실행은_dead_letter_와_구분되는_까닭으로_닫는다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE));

            sut.closeOverdueInQueue(ID);

            assertThat(closedReport().closedBy())
                    .isEqualTo(EngineReport.ClosedBy.QUEUE_OVERDUE)
                    .isNotEqualTo(EngineReport.ClosedBy.DEAD_LETTERED);
        }

        // B10 · B11 — 대기 화면이 끝나도록 구독자에게 알린다. 실패 코드는 dead letter(expired)로 닫을 때와 같다.
        @Test
        void 안전망이_닫으면_구독자에게_ABC_QUEUE_EXPIRED_플랫폼_탓_실패를_알린다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE));

            sut.closeOverdueInQueue(ID);

            assertThat(listener.last()).satisfies(progress -> {
                assertThat(progress.id()).isEqualTo(ID);
                assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
                assertThat(progress.total()).isEqualTo(2);
                assertThat(progress.failure()).isEqualTo(
                        new ExecutionProgress.Failure(null, "ABC_QUEUE_EXPIRED", "platform"));
            });
        }

        // B12 — 닫기가 커밋되지 않았는데 구독자에게 실패를 알리면 화면과 DB 가 어긋난다. 다음 차례에 다시 본다.
        @Test
        void 닫다_실패하면_구독자에게_알리지_않고_다음_차례에_다시_닫는다() {
            queuedDispatchedAt(NOW.minus(QUEUE_LIMIT_AND_QUEUE_GRACE));
            given(reservationManager.finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class)))
                    .willThrow(new DataAccessResourceFailureException("db down"))
                    .willReturn(true);

            catchThrowable(() -> sut.closeOverdueInQueue(ID));

            assertThat(listener.seen).isEmpty();

            sut.closeOverdueInQueue(ID);

            then(reservationManager).should(times(2))
                    .finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
            assertThat(listener.last().status()).isEqualTo(ExecutionStatus.FAILED);
        }

        // B13 — 유예는 설정값이다. 큐 대기 한도는 토폴로지 TTL(10분) 그대로라 유예 2분이면 12분이 경계다.
        @Test
        void 유예를_2분으로_두면_넘긴_지_12분에_닫는다() {
            ExecutionTracker shortGrace = trackerWithQueueGrace(Duration.ofMinutes(2));
            queuedDispatchedAt(NOW.minus(Duration.ofMinutes(12)));

            shortGrace.closeOverdueInQueue(ID);

            then(reservationManager).should()
                    .finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class));
        }

        @Test
        void 유예를_2분으로_두면_넘긴_지_12분에서_1ms_모자랄_때_닫지_않는다() {
            ExecutionTracker shortGrace = trackerWithQueueGrace(Duration.ofMinutes(2));
            queuedDispatchedAt(NOW.minus(Duration.ofMinutes(12)).plusMillis(1));

            shortGrace.closeOverdueInQueue(ID);

            then(reservationManager).shouldHaveNoInteractions();
        }

        // 넘긴 채 QUEUED 인 실행이고, 잠근 뒤에도 QUEUED 라 닫힌다(finishQueued 가 true).
        private void queuedDispatchedAt(Instant dispatchedAt) {
            given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED));
            given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(dispatchedAt));
            given(reservationManager.finishQueued(eq(ID), eq(ExecutionStatus.FAILED), any(EngineReport.class)))
                    .willReturn(true);
        }

        private EngineReport closedReport() {
            ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);

            then(reservationManager).should().finishQueued(eq(ID), eq(ExecutionStatus.FAILED), report.capture());

            return report.getValue();
        }
    }

    private ExecutionTracker trackerWithQueueGrace(Duration queueGrace) {
        return new ExecutionTracker(aiRuntimeClient, runQueuePublisher, executionReader, executionWriter,
                reservationManager, resultCollector, store,
                new ExecutionWatchProperties(Duration.ofSeconds(60), Duration.ofSeconds(60)),
                new ExecutionRecoveryProperties(queueGrace), List.of(listener), clock);
    }

    private void accept() {
        acceptDispatchedAt(NOW);
    }

    private void acceptDispatchedAt(Instant dispatchedAt) {
        given(executionReader.read(ID)).willReturn(execution(ExecutionStatus.QUEUED), execution(ExecutionStatus.RUNNING));
        given(executionReader.readDispatchedAt(ID)).willReturn(Optional.of(dispatchedAt));

        sut.apply(RunEventFixtures.accepted(RUN_ID, 1, 1));
    }

    private static Execution execution(ExecutionStatus status) {
        return execution(ID, status, 2);
    }

    private static Execution execution(ExecutionId id, ExecutionStatus status, int promptCount) {
        return Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                UserId.of(1L), RunId.of(id.value().toString()), status, promptCount, null, false,
                AbcAmount.of(new BigDecimal("6")));
    }

    private static Execution undispatched() {
        return Execution.restore(ID, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                UserId.of(1L), null, ExecutionStatus.QUEUED, 2, null, false, AbcAmount.of(new BigDecimal("6")));
    }

    private static RunStatus status(String state) {
        return new RunStatus(RUN_ID, state, new RunStatus.Progress(1, 2, 50, "n2", "가정통신문 작성"),
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    private static final class RecordingListener implements ExecutionProgressListener {
        private final List<ExecutionProgress> seen = new ArrayList<>();

        @Override
        public void onProgress(ExecutionProgress progress) {
            seen.add(progress);
        }

        private ExecutionProgress last() {
            assertThat(seen).isNotEmpty();

            return seen.getLast();
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
