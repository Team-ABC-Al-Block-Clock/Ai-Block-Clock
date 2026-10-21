package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunRecord;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

// 근거 적기가 실행의 핵심 흐름을 막지 않는지 본다. 근거는 실패해도 던지지 않고, 엔진 호출도 여기서만 한다.
class ExecutionEvidenceRecorderTest {
    private static final ExecutionId ID = ExecutionId.of(UUID.randomUUID());

    private final ExecutionEvidenceWriter writer = mock(ExecutionEvidenceWriter.class);
    private final ExecutionRepository executionRepository = mock(ExecutionRepository.class);
    private final ExecutionFailureRepository executionFailureRepository = mock(ExecutionFailureRepository.class);
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);
    private final ExecutionEvidenceRecorder sut = new ExecutionEvidenceRecorder(writer, executionRepository,
            executionFailureRepository, aiRuntimeClient, Clock.systemUTC());

    @Test
    void 근거를_적다_실패해도_던지지_않는다_백필이_다시_적는다() {
        given(writer.record(any(), any())).willThrow(new IllegalStateException("db down"));

        assertThatCode(() -> assertThat(sut.record(ID, EngineReport.lost(null))).isFalse())
                .doesNotThrowAnyException();
    }

    // 엔진이 끝을 알린 실행은 원본의 상태로 단계 표를 채운다. 닫을 때 들은 상태(polled)는 원본(recorded)으로 바뀐다.
    @Test
    void 엔진이_끝을_알린_실행은_근거_스레드가_원본을_받아_적는다() {
        RunStatus polled = run("succeeded", 1);
        RunStatus recorded = run("succeeded", 2);
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));
        given(aiRuntimeClient.readRecord("run-1")).willReturn(new RunRecord(recorded, "{\"raw\":1}"));

        sut.record(ID, EngineReport.reported(polled, null));

        then(writer).should().record(ID, EngineReport.reported(recorded, "{\"raw\":1}"));
    }

    @Test
    void 원본을_받지_못해도_마지막으로_본_상태로_적는다() {
        RunStatus polled = run("succeeded");
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));
        given(aiRuntimeClient.readRecord("run-1")).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));

        sut.record(ID, EngineReport.reported(polled, null));

        then(writer).should().record(ID, EngineReport.reported(polled, null));
    }

    @Test
    void 엔진이_잊은_실행은_원본을_물으러_가지_않는다() {
        sut.record(ID, EngineReport.lost(null));

        then(aiRuntimeClient).should(never()).readRecord(anyString());
    }

    // 완료 이벤트(RunEvent)의 snapshot 원문을 받아 닫았다. 노드 출력까지 들었으니 엔진에 다시 묻지 않는다.
    @Test
    void 이벤트의_snapshot_원문으로_닫은_실행은_엔진에_묻지_않고_그_원문으로_적는다() {
        RunStatus snapshot = run("succeeded");
        String raw = "{\"run_id\":\"run-1\",\"status\":\"succeeded\",\"nodes\":[{\"output_payload\":\"{}\"}]}";
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));

        sut.record(ID, EngineReport.reported(snapshot, raw));

        then(aiRuntimeClient).should(never()).readRecord(anyString());
        then(writer).should().record(ID, EngineReport.reported(snapshot, raw));
    }

    // dead letter 로 닫은 실행은 런타임이 받지 못했거나 끝내지 못한 것이다. 물을 원본이 없다 — LOST 와 같다.
    @Test
    void dead_letter_로_닫은_실행은_엔진에_원본을_묻지_않는다() {
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));

        sut.record(ID, EngineReport.deadLettered(null, "ABC_QUEUE_EXPIRED"));

        then(aiRuntimeClient).should(never()).readRecord(anyString());
        then(writer).should().record(ID, EngineReport.deadLettered(null, "ABC_QUEUE_EXPIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ABC_QUEUE_EXPIRED", "ABC_RUNTIME_RETRY_EXHAUSTED", "ABC_RUNTIME_DEAD_LETTERED"})
    void 백필은_dead_letter_실패_코드로_닫힌_실행을_dead_letter_로_적고_엔진에_묻지_않는다(String failureCode) {
        ExecutionEntity closed = dispatched();
        given(executionRepository.findClosedWithoutRunRecord(any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(closed));
        given(executionFailureRepository.findById(ID.value())).willReturn(Optional.of(new ExecutionFailureEntity(
                ID.value(), null, failureCode, ExecutionFault.PLATFORM, false,
                null, null)));
        given(writer.record(eq(ID), any(EngineReport.class))).willReturn(true);

        sut.backfill(10);

        ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);
        then(writer).should().record(eq(ID), report.capture());
        assertThat(report.getValue().closedBy()).isEqualTo(EngineReport.ClosedBy.DEAD_LETTERED);
        then(aiRuntimeClient).should(never()).readRecord(anyString());
    }

    // 닫은 스레드(리스너 · 감시)는 근거를 기다리지 않는다. 근거 스레드가 받아 적는다.
    @Test
    void 커밋_뒤_이벤트는_닫은_스레드를_붙잡지_않고_근거_스레드에서_적는다() {
        CountDownLatch writerAnswers = new CountDownLatch(1);
        willAnswer(invocation -> writerAnswers.await(5, TimeUnit.SECONDS)).given(writer).record(eq(ID), any());

        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> sut.onClosed(new ExecutionClosed(ID, EngineReport.undispatched())));
        writerAnswers.countDown();

        then(writer).should(timeout(1_000)).record(ID, EngineReport.undispatched());
    }

    // 근거 스레드가 살아 있다고 서버가 내려가지 못하면 안 된다. 스레드 덤프에서 알아볼 수 있는 이름을 단다.
    @Test
    void 근거는_근거_이름을_단_데몬_스레드에서_적는다() {
        AtomicReference<Thread> recordedOn = new AtomicReference<>();
        willAnswer(invocation -> {
            recordedOn.set(Thread.currentThread());

            return true;
        }).given(writer).record(eq(ID), any());

        sut.onClosed(new ExecutionClosed(ID, EngineReport.undispatched()));

        await().atMost(Duration.ofSeconds(1)).until(() -> recordedOn.get() != null);
        assertThat(recordedOn.get().isDaemon()).isTrue();
        assertThat(recordedOn.get().getName()).startsWith("execution-evidence-");
    }

    // 서버가 내려가는 중이다. 빠진 근거는 다음 기동의 백필이 적는다.
    @Test
    void 내려간_뒤에_닫힌_실행의_근거는_적지_않는다() {
        sut.destroy();

        assertThatCode(() -> sut.onClosed(new ExecutionClosed(ID, EngineReport.undispatched())))
                .doesNotThrowAnyException();

        then(writer).should(after(300).never()).record(any(), any());
    }

    // 우리가 끊은 실행은 끊을 때 본 상태가 판단의 근거다. 원본(끊은 뒤 엔진이 어디까지 갔는지)은 덧붙여만 둔다.
    @Test
    void 우리가_끊은_실행은_끊을_때_본_상태를_그대로_두고_엔진_원본만_덧붙인다() {
        RunStatus lastSeen = run("running", 1);
        RunStatus recorded = run("succeeded", 2);
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));
        given(aiRuntimeClient.readRecord("run-1")).willReturn(new RunRecord(recorded, "{\"raw\":1}"));

        sut.record(ID, EngineReport.stalled(lastSeen, null));

        then(writer).should().record(ID,
                new EngineReport(EngineReport.ClosedBy.STALLED, lastSeen, "{\"raw\":1}", null));
    }

    @Test
    void 끊을_때_본_상태가_없으면_엔진_원본의_상태로_적는다() {
        RunStatus recorded = run("running", 2);
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));
        given(aiRuntimeClient.readRecord("run-1")).willReturn(new RunRecord(recorded, "{\"raw\":1}"));

        sut.record(ID, EngineReport.stalled(null, null));

        then(writer).should().record(ID,
                new EngineReport(EngineReport.ClosedBy.STALLED, recorded, "{\"raw\":1}", null));
    }

    @Test
    void 넘긴_기록이_없는_실행은_엔진에_묻지_않고_받은_그대로_적는다() {
        RunStatus lastSeen = run("running", 1);
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(execution(ID.value(), null)));

        sut.record(ID, EngineReport.stalled(lastSeen, null));

        then(aiRuntimeClient).should(never()).readRecord(anyString());
        then(writer).should().record(ID, EngineReport.stalled(lastSeen, null));
    }

    @Test
    void 엔진이_원본을_주지_않으면_받은_그대로_적는다() {
        RunStatus lastSeen = run("running", 1);
        given(executionRepository.findById(ID.value())).willReturn(Optional.of(dispatched()));
        given(aiRuntimeClient.readRecord("run-1")).willReturn(null);

        sut.record(ID, EngineReport.stalled(lastSeen, null));

        then(writer).should().record(ID, EngineReport.stalled(lastSeen, null));
    }

    @Test
    void 백필은_기한으로_끊은_실행을_STALLED_로_되짚고_엔진_원본을_받으러_간다() {
        backfillTarget(dispatched(), ExecutionProgress.STALLED);

        sut.backfill(10);

        assertThat(backfilledReport().closedBy()).isEqualTo(EngineReport.ClosedBy.STALLED);
        then(aiRuntimeClient).should().readRecord("run-1");
    }

    // 넘기지 못한 채 버려진 실행이다(B10). 엔진은 이 실행을 받은 적이 없다.
    @Test
    void 백필은_넘긴_기록이_없는_실행을_넘기지_못한_실행으로_되짚고_엔진에_묻지_않는다() {
        backfillTarget(execution(ID.value(), null), ExecutionFailure.UNREACHABLE);

        sut.backfill(10);

        assertThat(backfilledReport().closedBy()).isEqualTo(EngineReport.ClosedBy.UNDISPATCHED);
        then(aiRuntimeClient).should(never()).readRecord(anyString());
    }

    @Test
    void 백필은_엔진이_잊은_실행을_LOST_로_되짚는다() {
        backfillTarget(dispatched(), ExecutionProgress.LOST);

        sut.backfill(10);

        assertThat(backfilledReport().closedBy()).isEqualTo(EngineReport.ClosedBy.LOST);
        then(aiRuntimeClient).should(never()).readRecord(anyString());
    }

    @Test
    void 백필은_실패_판정이_없는_실행을_엔진이_끝을_알린_실행으로_되짚고_원본을_받으러_간다() {
        backfillTarget(dispatched(), null);

        sut.backfill(10);

        assertThat(backfilledReport().closedBy()).isEqualTo(EngineReport.ClosedBy.ENGINE);
        then(aiRuntimeClient).should().readRecord("run-1");
    }

    @Test
    void 백필은_근거를_새로_적은_실행_수를_돌려준다() {
        given(executionRepository.findClosedWithoutRunRecord(any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(execution(UUID.randomUUID(), null), execution(UUID.randomUUID(), null),
                        execution(UUID.randomUUID(), null)));
        given(writer.record(any(), any())).willReturn(true, false, true);

        assertThat(sut.backfill(10)).isEqualTo(2);
    }

    private void backfillTarget(ExecutionEntity closed, String failureCode) {
        given(executionRepository.findClosedWithoutRunRecord(any(Instant.class), any(Pageable.class)))
                .willReturn(List.of(closed));
        given(executionRepository.findById(closed.getId())).willReturn(Optional.of(closed));

        if (failureCode != null) {
            given(executionFailureRepository.findById(closed.getId())).willReturn(Optional.of(new ExecutionFailureEntity(
                    closed.getId(), null, failureCode, ExecutionFault.PLATFORM, false, null, null)));
        }
    }

    private EngineReport backfilledReport() {
        ArgumentCaptor<EngineReport> report = ArgumentCaptor.forClass(EngineReport.class);

        then(writer).should().record(eq(ID), report.capture());

        return report.getValue();
    }

    private static ExecutionEntity dispatched() {
        return execution(ID.value(), "run-1");
    }

    private static ExecutionEntity execution(UUID id, String runId) {
        return new ExecutionEntity(id, UUID.randomUUID(), UUID.randomUUID(), 1L, runId,
                com.aiblockclock.enums.execution.ExecutionStatus.SUCCEEDED, 1, null, null, null, false, null);
    }

    private static RunStatus run(String status) {
        return new RunStatus("run-1", status, null, List.of(), List.of(), List.of(), null, null, false, null);
    }

    // done 으로 서로 다른 상태를 만든다. 근거가 어느 쪽 상태로 적혔는지 가를 수 있다.
    private static RunStatus run(String status, int done) {
        return new RunStatus("run-1", status, new RunStatus.Progress(done, 2, done * 50, null, null),
                List.of(), List.of(), List.of(), null, null, false, null);
    }
}
