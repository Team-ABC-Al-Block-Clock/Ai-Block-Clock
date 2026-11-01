package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunRecord;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// 정산 근거를 실행의 핵심 흐름(접수 → 이벤트 적용 → 닫기 · 환불 · 정산) 밖에서 적는다.
//   1. 닫는 트랜잭션이 커밋되면 이벤트를 받아 근거 전용 스레드에 넘긴다. 리스너 스레드 · 사용자 요청 스레드는 기다리지 않는다
//   2. 근거 스레드가 단계 표와 함께 적는다. 완료 이벤트로 닫은 실행은 snapshot 원문(노드 출력 포함)을 그대로 쓰고,
//      감시가 닫은 실행만 엔진 원본을 받는다. 엔진 호출도 여기서 한다
//   3. 큐가 넘치거나, 적다 실패하거나, 그 사이 서버가 내려가 빠진 근거는 백필(backfill)이 닫힌 실행을 훑어 다시 적는다
// 근거가 늦게 적히거나 한 번 실패해도 돈은 이미 맞게 옮겨져 있다. 근거는 돈의 기록(장부 · 정산 내역 · 실패 판정)을
// 설명하는 것이고, 돈의 기록 자체는 닫는 트랜잭션에 그대로 남는다.
@Slf4j
@Component
public class ExecutionEvidenceRecorder implements DisposableBean {
    // 엔진은 끝난 실행을 이만큼 들고 있다(RUN_RETENTION_HOURS 기본 24시간). 그보다 오래된 실행은 원본을 받을 수 없어 백필하지 않는다.
    static final Duration BACKFILL_WINDOW = Duration.ofHours(24);
    private static final int QUEUE_CAPACITY = 1_000;

    private final ExecutionEvidenceWriter executionEvidenceWriter;
    private final ExecutionRepository executionRepository;
    private final ExecutionFailureRepository executionFailureRepository;
    private final AiRuntimeClient aiRuntimeClient;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    public ExecutionEvidenceRecorder(ExecutionEvidenceWriter executionEvidenceWriter,
                                     ExecutionRepository executionRepository,
                                     ExecutionFailureRepository executionFailureRepository,
                                     AiRuntimeClient aiRuntimeClient, Clock clock) {
        this.executionEvidenceWriter = executionEvidenceWriter;
        this.executionRepository = executionRepository;
        this.executionFailureRepository = executionFailureRepository;
        this.aiRuntimeClient = aiRuntimeClient;
        this.clock = clock;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY), namedThreads(), new ThreadPoolExecutor.AbortPolicy());
    }

    // 닫는 트랜잭션이 커밋된 직후, 닫은 스레드에서 불린다. 넘기기만 하고 곧바로 돌아간다.
    // 큐가 차면 버린다 — 닫은 스레드를 붙잡지 않는 것이 먼저이고, 버린 근거는 백필이 채운다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClosed(ExecutionClosed closed) {
        try {
            executor.execute(() -> record(closed.id(), closed.report()));
        } catch (RejectedExecutionException exception) {
            log.warn("근거 큐가 차서 지금은 적지 않는다. 백필이 다시 적는다: execution={}", closed.id().value());
        }
    }

    // 닫혔는데 근거가 없는 실행을 찾아 적는다. 무엇이 닫았는지는 남은 실패 판정으로 되짚는다.
    // 원본은 엔진에 다시 묻는다. 그 사이 엔진이 잊었으면 원본 없이 적고, 그 사실이 근거의 note 에 남는다.
    public int backfill(int batchSize) {
        List<ExecutionEntity> missing = executionRepository.findClosedWithoutRunRecord(
                clock.instant().minus(BACKFILL_WINDOW), PageRequest.of(0, batchSize));
        int recorded = 0;

        for (ExecutionEntity execution : missing) {
            ExecutionId id = ExecutionId.of(execution.getId());

            if (record(id, reportOf(execution))) {
                recorded++;
            }
        }

        return recorded;
    }

    // 실패해도 던지지 않는다. 근거를 못 적은 것은 백필이 다음 차례에 다시 본다.
    boolean record(ExecutionId id, EngineReport report) {
        try {
            return executionEvidenceWriter.record(id, withEngineRecord(id, report));
        } catch (RuntimeException exception) {
            log.warn("정산 근거를 적지 못했다. 백필이 다시 적는다: execution={}", id.value(), exception);

            return false;
        }
    }

    // 엔진이 끝을 알렸거나 우리가 기한으로 끊은 실행은 엔진이 아직 원본을 들고 있다. 받아서 근거로 쓴다.
    // 이미 원문을 든 실행(완료 이벤트) · 엔진이 잊은 실행(LOST) · 넘기지 못한 실행 · dead letter 로 닫은 실행 ·
    // 큐 대기 안전망이 닫은 실행(런타임이 받은 적이 없다)은 물을 것이 없다.
    private EngineReport withEngineRecord(ExecutionId id, EngineReport report) {
        if (report.raw() != null
                || report.closedBy() == EngineReport.ClosedBy.LOST
                || report.closedBy() == EngineReport.ClosedBy.UNDISPATCHED
                || report.closedBy() == EngineReport.ClosedBy.DEAD_LETTERED
                || report.closedBy() == EngineReport.ClosedBy.QUEUE_OVERDUE) {
            return report;
        }

        String runId = executionRepository.findById(id.value()).map(ExecutionEntity::getRunId).orElse(null);

        if (runId == null) {
            return report;
        }

        try {
            RunRecord record = aiRuntimeClient.readRecord(runId);

            if (record == null || record.status() == null) {
                return report;
            }

            // 엔진이 끝을 알린 실행은 원본의 상태로 단계 표를 채운다. 우리가 끊은 실행은 끊을 때 본 상태가 판단의 근거라
            // 그대로 두고, 원본(끊은 뒤 엔진이 어디까지 갔는지)은 덧붙여만 둔다.
            return report.closedBy() == EngineReport.ClosedBy.ENGINE && record.status().isFinished()
                    ? EngineReport.reported(record.status(), record.json())
                    : new EngineReport(report.closedBy(), report.run() == null ? record.status() : report.run(),
                    record.json(), report.failureCode());
        } catch (RuntimeException exception) {
            log.info("근거로 남길 엔진 원본을 받지 못했다. 마지막으로 본 상태로 적는다: execution={} {}",
                    id.value(), exception.getMessage());

            return report;
        }
    }

    // dead letter 로 닫은 실행은 넘긴 기록이 없을 수도 있다(confirm 을 받기 전에 큐에서 만료됐다). 그래서 그것부터 본다.
    private EngineReport reportOf(ExecutionEntity execution) {
        String failureCode = executionFailureRepository.findById(execution.getId())
                .map(ExecutionFailureEntity::getFailureCode)
                .orElse(null);

        if (DeadLetterReason.isFailureCodeOf(failureCode)) {
            return EngineReport.deadLettered(null, failureCode);
        }

        if (execution.getRunId() == null) {
            return EngineReport.undispatched();
        }

        if (ExecutionProgress.STALLED.equals(failureCode)) {
            return EngineReport.stalled(null, null);
        }

        if (ExecutionProgress.LOST.equals(failureCode)) {
            return EngineReport.lost(null);
        }

        return EngineReport.reported(null, null);
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }

    private static java.util.concurrent.ThreadFactory namedThreads() {
        AtomicInteger sequence = new AtomicInteger();

        return runnable -> {
            Thread thread = new Thread(runnable, "execution-evidence-" + sequence.incrementAndGet());
            thread.setDaemon(true);

            return thread;
        };
    }
}
