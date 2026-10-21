package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 실행을 이벤트로 따라가고(abc.events.backend), 이벤트가 끊긴 실행만 엔진에 HTTP 로 한 번 묻는다(감시).
//   apply              RunEvent 를 실행에 적용한다. 상태는 이벤트 type 으로 정하고, (attempt, seq) 가 거꾸로 온 것은 버린다
//   begin · isWatching 감시에 붙인다(복구). RUNNING 실행만 붙는다 — 큐에서 기다리는 실행은 TTL 과 dead letter 가 맡는다
//   check              마지막 이벤트에서 기한(단계 수 × per-step + grace)이 지난 실행만 엔진에 묻는다
//   closeDeadLettered  abc.runs.dead 로 온 실행을 플랫폼 탓으로 닫는다
// 닫기 · 환불 · 정산은 ExecutionReservationManager.finish 한 곳에서 한다. 이미 닫힌 실행은 거기서 걸러져 두 번 닫히지 않는다.
// 이벤트를 적용하다 실패하면(DB 오류 등) 던진다. 리스너가 다시 시도하고, 적용하지 못한 순번은 남기지 않아 다시 온 것을 버리지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(ExecutionWatchProperties.class)
public class ExecutionTracker {
    // abc.runs.submit 의 x-message-ttl 이다 (ai/docs/integration-guide.md §4.1).
    // 넘긴 뒤 이만큼 + grace 가 지나도록 엔진이 모르면 다시 올 일이 없다.
    private static final Duration QUEUE_WAIT_LIMIT = Duration.ofMinutes(10);

    private final AiRuntimeClient aiRuntimeClient;
    private final RunQueuePublisher runQueuePublisher;
    private final ExecutionReader executionReader;
    private final ExecutionWriter executionWriter;
    private final ExecutionReservationManager executionReservationManager;
    private final ExecutionResultCollector executionResultCollector;
    private final ExecutionProgressStore progressStore;
    private final ExecutionWatchProperties properties;
    private final List<ExecutionProgressListener> listeners;
    private final Clock clock;

    private final ConcurrentMap<ExecutionId, ExecutionWatch> watching = new ConcurrentHashMap<>();

    public void apply(RunEvent event) {
        Optional<ExecutionId> known = idOf(event.executionId());

        if (known.isEmpty()) {
            return;
        }

        ExecutionId id = known.get();
        ExecutionWatch watch = watching.get(id);

        if (watch != null && !watch.isBehind(event.attempt(), event.seq())) {
            log.debug("이미 적용한 것보다 앞선 이벤트라 버린다: execution={} attempt={} seq={}",
                    id.value(), event.attempt(), event.seq());

            return;
        }

        Optional<Execution> found = find(id);

        if (found.isEmpty() || found.get().getStatus().isFinished()) {
            return;
        }

        Execution execution = found.get();

        // confirm 을 기록하기 전에 이벤트가 먼저 왔다. 런타임이 받았으니 넘긴 것이다. 감시 · 근거가 실행 번호로 엔진을 찾는다.
        if (!execution.isDispatched()) {
            executionReservationManager.markDispatched(id, clock.instant());
        }

        switch (event.type()) {
            case ACCEPTED, PROGRESS -> follow(execution, event);
            case SUCCEEDED -> close(execution, ExecutionStatus.SUCCEEDED, event);
            case FAILED, REJECTED -> close(execution, ExecutionStatus.FAILED, event);
        }
    }

    // 큐에서 기다리는 실행은 붙이지 않는다. 이미 감시 중이면 기한을 새로 잡지 않고 false 다.
    public boolean begin(Execution execution) {
        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            return false;
        }

        return watching.putIfAbsent(execution.getId(), watchOf(execution, clock.instant())) == null;
    }

    public boolean isWatching(ExecutionId id) {
        return watching.containsKey(id);
    }

    // 한 건이 실패해도 나머지는 본다. 실패한 건은 기한이 지난 채로 남아 다음 차례에 다시 묻는다.
    public void check() {
        Instant now = clock.instant();

        for (ExecutionWatch watch : watching.values()) {
            if (!watch.isOverdue(now)) {
                continue;
            }

            try {
                askEngine(watch, now);
            } catch (RuntimeException exception) {
                log.warn("기한을 넘긴 실행을 닫지 못했다. 다음 차례에 다시 본다: execution={}", watch.id().value(), exception);
            }
        }
    }

    // 사용자는 예치를 전액 돌려받는다. 그 전에 성공한 단계가 있으면 그 공급자 몫은 플랫폼이 낸다 — 마지막으로 받은 snapshot 으로 가린다.
    public void closeDeadLettered(ExecutionId id, DeadLetterReason reason) {
        Optional<Execution> found = find(id);

        if (found.isEmpty() || found.get().getStatus().isFinished()) {
            return;
        }

        ExecutionWatch watch = watching.get(id);
        RunStatus lastSeen = watch == null ? null : watch.lastSnapshot();

        executionReservationManager.finish(id, ExecutionStatus.FAILED,
                EngineReport.deadLettered(lastSeen, reason.failureCode()));
        stopAndNotify(ExecutionProgress.deadLettered(id, lastProgressOf(id), found.get().getPromptCount(),
                reason.failureCode()));
    }

    // 우리 실행 번호는 UUID 다. UUID 가 아니면 모르는 실행이라 비어 있다. 다시 와도 알게 되지 않는다.
    private static Optional<ExecutionId> idOf(String executionId) {
        try {
            return Optional.of(ExecutionId.of(executionId));
        } catch (IllegalArgumentException malformed) {
            log.warn("실행 번호가 UUID 가 아니라 모르는 실행의 메시지로 보고 넘어간다: execution={}", executionId);

            return Optional.empty();
        }
    }

    // 모르는 실행이면 비어 있다. 지워졌거나 다른 환경의 실행이라 할 것이 없다.
    private Optional<Execution> find(ExecutionId id) {
        try {
            return Optional.of(executionReader.read(id));
        } catch (CoreException exception) {
            if (exception.getErrorType() != ErrorType.EXECUTION_NOT_FOUND) {
                throw exception;
            }

            log.warn("모르는 실행의 메시지라 넘어간다: execution={}", id.value());

            return Optional.empty();
        }
    }

    // RUNNING 으로 옮기는 쓰기는 처음 한 번뿐이다. 이벤트를 받을 때마다 기한을 그 이벤트부터 다시 잰다.
    private void follow(Execution execution, RunEvent event) {
        ExecutionId id = execution.getId();

        if (execution.getStatus() != ExecutionStatus.RUNNING) {
            executionWriter.moveTo(id, ExecutionStatus.RUNNING);
        }

        Instant now = clock.instant();
        watching.compute(id, (key, watch) -> (watch == null ? watchOf(execution, now) : watch)
                .heard(event.attempt(), event.seq(), event.snapshot(), now));

        ExecutionProgress progress = ExecutionProgress.of(id, ExecutionStatus.RUNNING, event.snapshot(),
                execution.getPromptCount());
        progressStore.put(progress);
        notifyListeners(progress);
    }

    // snapshot 원문을 근거로 넘긴다. 노드 출력까지 들었으니 근거를 적을 때 엔진에 다시 묻지 않는다.
    private void close(Execution execution, ExecutionStatus status, RunEvent event) {
        ExecutionId id = execution.getId();

        executionReservationManager.finish(id, status, EngineReport.reported(event.snapshot(), event.snapshotJson()));

        if (status == ExecutionStatus.SUCCEEDED) {
            executionResultCollector.collect(id);
        }

        stopAndNotify(ExecutionProgress.of(id, status, event.snapshot(), execution.getPromptCount()));
    }

    private void askEngine(ExecutionWatch watch, Instant now) {
        RunStatus run;

        try {
            run = aiRuntimeClient.read(watch.runId());
        } catch (AiClientException exception) {
            unanswered(watch, exception, now);

            return;
        }

        if (!run.isFinished()) {
            log.warn("이벤트가 끊긴 채 엔진이 아직 돌거나 기다린다고 해 끊는다: execution={}", watch.id().value());
            stall(watch, run);

            return;
        }

        ExecutionStatus status = ExecutionProgress.toStatus(run.state());

        executionReservationManager.finish(watch.id(), status, EngineReport.reported(run, null));

        if (status == ExecutionStatus.SUCCEEDED) {
            executionResultCollector.collect(watch.id());
        }

        stopAndNotify(ExecutionProgress.of(watch.id(), run, watch.stepCount()));
    }

    // 런타임이 죽어도 메시지는 다시 배달되므로, 넘긴 지 큐 대기 한도 + grace 가 지나기 전에는 닫지 않고 기한을 새로 건다.
    // 그 뒤에도 엔진이 모르면 잃은 것이고(LOST), 닿지 못하면 기한 안에 아무 말도 듣지 못한 것이다(STALLED).
    private void unanswered(ExecutionWatch watch, AiClientException exception, Instant now) {
        if (!isPastQueueWait(watch.id(), now)) {
            log.info("엔진이 실행을 모르거나 닿지 않지만 아직 다시 배달될 수 있어 기다린다: execution={} {}",
                    watch.id().value(), exception.getError());
            watch.rearm(now);

            return;
        }

        if (exception.getError() != AiClientError.RUN_NOT_FOUND) {
            log.warn("엔진에 닿지 못한 채 큐 대기 한도가 지나 끊는다: execution={}", watch.id().value());
            stall(watch, watch.lastSnapshot());

            return;
        }

        log.warn("엔진이 실행을 모르고 큐 대기 한도가 지나 플랫폼 탓으로 닫는다: execution={}", watch.id().value());
        executionReservationManager.finish(watch.id(), ExecutionStatus.FAILED, EngineReport.lost(watch.lastSnapshot()));
        stopAndNotify(ExecutionProgress.lost(watch.id(), lastProgressOf(watch.id()), watch.stepCount()));
    }

    // 넘긴 시각이 없으면(outbox 가 생기기 전에 넘긴 실행) 큐에서 기다릴 일이 없던 것으로 본다.
    private boolean isPastQueueWait(ExecutionId id, Instant now) {
        return executionReader.readDispatchedAt(id)
                .map(dispatchedAt -> !now.isBefore(dispatchedAt.plus(QUEUE_WAIT_LIMIT).plus(properties.grace())))
                .orElse(true);
    }

    // 끊은 뒤에도 엔진이 돌면 아무도 받지 않는 LLM 비용이 든다. 멈추게 하되, 취소를 걸지 못해도 닫는다.
    private void stall(ExecutionWatch watch, RunStatus lastSeen) {
        cancelQuietly(watch.runId());
        executionReservationManager.finish(watch.id(), ExecutionStatus.FAILED, EngineReport.stalled(lastSeen, null));
        stopAndNotify(ExecutionProgress.stalled(watch.id(), lastProgressOf(watch.id()), watch.stepCount()));
    }

    private void cancelQuietly(String runId) {
        try {
            runQueuePublisher.cancel(runId);
        } catch (RuntimeException exception) {
            log.info("실행 취소를 보내지 못했다(브로커에 닿지 않음): run={} {}", runId, exception.getMessage());
        }
    }

    private ExecutionWatch watchOf(Execution execution, Instant heardAt) {
        return new ExecutionWatch(execution.getId(), execution.getPromptCount(),
                properties.deadline(execution.getPromptCount()), heardAt);
    }

    private ExecutionProgress lastProgressOf(ExecutionId id) {
        return progressStore.find(id).orElse(null);
    }

    // 닫힌 실행은 감시와 진행 저장소에서 뺀 뒤 알린다. 알림을 받고 곧바로 조회해도 닫힌 DB 상태로 답한다.
    private void stopAndNotify(ExecutionProgress closed) {
        watching.remove(closed.id());
        progressStore.forget(closed.id());
        notifyListeners(closed);
    }

    private void notifyListeners(ExecutionProgress progress) {
        for (ExecutionProgressListener listener : listeners) {
            try {
                listener.onProgress(progress);
            } catch (RuntimeException exception) {
                log.warn("진행 상황을 전하지 못했다: execution={}", progress.id().value(), exception);
            }
        }
    }
}
