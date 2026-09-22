package com.aiblockclock.enums.execution;

public enum ExecutionStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isFinished() {
        return this == SUCCEEDED || this == FAILED;
    }
}
