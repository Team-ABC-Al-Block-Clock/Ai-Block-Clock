package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutionMonitor {
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWriter executionWriter;
    private final ExecutionReservationManager executionReservationManager;
    private final ExecutionProgressStore progressStore;
    private final ExecutionPollingProperties properties;
    private final List<ExecutionProgressListener> listeners;
    private final Clock clock;

    private final ConcurrentMap<ExecutionId, ExecutionWatch> watching = new ConcurrentHashMap<>();

    public void begin(Execution execution) {
        ExecutionWatch watch = new ExecutionWatch(
                execution, properties.deadline(execution.getPromptCount()), clock.instant());

        watching.put(execution.getId(), watch);
        publish(ExecutionProgress.queued(execution.getId(), execution.getPromptCount()), watch, null);
    }

    public boolean poll(ExecutionId id) {
        ExecutionWatch watch = watching.get(id);

        if (watch == null) {
            return false;
        }

        RunStatus run;

        try {
            run = aiRuntimeClient.read(watch.runId().value());
        } catch (RuntimeException exception) {
            log.warn("진행 상황을 가져오지 못했다: execution={} {}", id.value(), exception.getMessage(), exception);

            return keepGoingOrGiveUp(watch);
        }

        ExecutionProgress progress = ExecutionProgress.of(id, run, watch.stepCount());

        if (watch.observe(progress, clock.instant())) {
            publish(progress, watch, run);
        }

        if (progress.status().isFinished()) {
            stop(id);

            return false;
        }

        return keepGoingOrGiveUp(watch);
    }

    private boolean keepGoingOrGiveUp(ExecutionWatch watch) {
        if (!watch.isStalled(clock.instant())) {
            return true;
        }

        log.warn("엔진이 기한 안에 아무 말도 하지 않아 실패로 끊는다: execution={}", watch.id().value());
        publish(ExecutionProgress.stalled(watch.id(), watch.last(), watch.stepCount()), watch, null);
        stop(watch.id());

        return false;
    }

    private void stop(ExecutionId id) {
        watching.remove(id);
        progressStore.forget(id);
    }

    // run 은 엔진이 알린 상태다. 접수 직후와 기한을 넘겨 우리가 끊을 때는 엔진에게 들은 것이 없어 비어 있다.
    private void publish(ExecutionProgress progress, ExecutionWatch watch, RunStatus run) {
        progressStore.put(progress);

        if (watch.movedTo(progress.status())) {
            remember(progress, run);
        }

        for (ExecutionProgressListener listener : listeners) {
            try {
                listener.onProgress(progress);
            } catch (RuntimeException exception) {
                log.warn("진행 상황을 전하지 못했다: execution={}", progress.id().value(), exception);
            }
        }
    }

    // 끝난 실행은 상태만 옮기지 않고 닫는다. 닫으면서 떼어 둔 것 중 쓰지 않은 만큼을 돌려준다.
    // 여기서 실패해도 우리 기록은 안 끝난 채로 남으므로, 조회가 엔진에 다시 물어 같은 자리에서 닫는다.
    private void remember(ExecutionProgress progress, RunStatus run) {
        try {
            if (progress.status().isFinished()) {
                executionReservationManager.finish(progress.id(), progress.status(), run);
            } else {
                executionWriter.moveTo(progress.id(), progress.status());
            }
        } catch (RuntimeException exception) {
            log.warn("실행 상태를 남기지 못했다: execution={}", progress.id().value(), exception);
        }
    }
}
