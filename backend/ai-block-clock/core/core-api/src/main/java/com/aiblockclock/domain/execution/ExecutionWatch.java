package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;

import java.time.Duration;
import java.time.Instant;

final class ExecutionWatch {
    private final Execution execution;
    private final ExecutionId id;
    private final RunId runId;
    private final int stepCount;
    private final Duration deadline;

    private ExecutionProgress last;
    private ExecutionStatus persisted;
    private Instant changedAt;

    ExecutionWatch(Execution execution, Duration deadline, Instant startedAt) {
        this.execution = execution;
        this.id = execution.getId();
        this.runId = execution.getRunId();
        this.stepCount = execution.getPromptCount();
        this.deadline = deadline;
        this.persisted = execution.getStatus();
        this.changedAt = startedAt;
    }

    ExecutionId id() {
        return id;
    }

    Execution execution() {
        return execution;
    }

    RunId runId() {
        return runId;
    }

    int stepCount() {
        return stepCount;
    }

    ExecutionProgress last() {
        return last;
    }

    boolean observe(ExecutionProgress progress, Instant now) {
        if (progress.equals(last)) {
            return false;
        }

        last = progress;
        changedAt = now;

        return true;
    }

    boolean movedTo(ExecutionStatus status) {
        if (status == persisted) {
            return false;
        }

        persisted = status;

        return true;
    }

    boolean isStalled(Instant now) {
        return !now.isBefore(changedAt.plus(deadline));
    }
}
