package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionPollingProperties;
import com.aiblockclock.domain.execution.ExecutionService;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class ExecutionProgressScheduler {
    private final TaskScheduler executionScheduler;
    private final ExecutionService executionService;
    private final ExecutionPollingProperties properties;
    private final Clock clock;

    public void follow(Execution execution) {
        executionService.beginWatching(execution);
        scheduleNext(execution.getId());
    }

    private void scheduleNext(ExecutionId id) {
        executionScheduler.schedule(() -> ask(id), clock.instant().plus(properties.interval()));
    }

    private void ask(ExecutionId id) {
        if (executionService.pollOnce(id)) {
            scheduleNext(id);
        }
    }
}
