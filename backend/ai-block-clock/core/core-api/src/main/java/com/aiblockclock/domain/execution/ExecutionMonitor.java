package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.enums.execution.ExecutionStatus;

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
    private final ExecutionResultHarvester executionResultHarvester;
    private final ExecutionProgressStore progressStore;
    private final ExecutionPollingProperties properties;
    private final List<ExecutionProgressListener> listeners;
    private final Clock clock;

    private final ConcurrentMap<ExecutionId, ExecutionWatch> watching = new ConcurrentHashMap<>();

    public void begin(Execution execution) {
        ExecutionWatch watch = new ExecutionWatch(
                execution, properties.deadline(execution.getPromptCount()), clock.instant());

        watching.put(execution.getId(), watch);
        publish(ExecutionProgress.queued(execution.getId(), execution.getPromptCount()), watch);
    }

    public boolean poll(ExecutionId id) {
        ExecutionWatch watch = watching.get(id);

        if (watch == null) {
            return false;
        }

        ExecutionProgress progress;

        try {
            progress = ExecutionProgress.of(id, aiRuntimeClient.read(watch.runId().value()), watch.stepCount());
        } catch (RuntimeException exception) {
            log.warn("진행 상황을 가져오지 못했다: execution={} {}", id.value(), exception.getMessage(), exception);

            return keepGoingOrGiveUp(watch);
        }

        if (watch.observe(progress, clock.instant())) {
            publish(progress, watch);
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
        publish(ExecutionProgress.stalled(watch.id(), watch.last(), watch.stepCount()), watch);
        stop(watch.id());

        return false;
    }

    private void stop(ExecutionId id) {
        watching.remove(id);
        progressStore.forget(id);
    }

    private void publish(ExecutionProgress progress, ExecutionWatch watch) {
        progressStore.put(progress);

        if (watch.movedTo(progress.status())) {
            remember(progress, watch);
        }

        for (ExecutionProgressListener listener : listeners) {
            try {
                listener.onProgress(progress);
            } catch (RuntimeException exception) {
                log.warn("진행 상황을 전하지 못했다: execution={}", progress.id().value(), exception);
            }
        }
    }

    private void remember(ExecutionProgress progress, ExecutionWatch watch) {
        try {
            executionWriter.moveTo(progress.id(), progress.status());

            if (progress.status() == ExecutionStatus.SUCCEEDED) {
                executionResultHarvester.harvest(watch.execution());
            }
        } catch (RuntimeException exception) {
            log.warn("실행 상태를 남기지 못했다: execution={}", progress.id().value(), exception);
        }
    }
}
