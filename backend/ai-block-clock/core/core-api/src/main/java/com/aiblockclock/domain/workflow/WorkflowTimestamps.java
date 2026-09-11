package com.aiblockclock.domain.workflow;

import java.time.Instant;

public record WorkflowTimestamps(Instant createdAt, Instant updatedAt, Instant lastExecutedAt) {
    private static final WorkflowTimestamps UNSAVED = new WorkflowTimestamps(null, null, null);

    public static WorkflowTimestamps unsaved() {
        return UNSAVED;
    }
}
