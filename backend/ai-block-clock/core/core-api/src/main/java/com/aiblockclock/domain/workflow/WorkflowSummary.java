package com.aiblockclock.domain.workflow;

import java.time.Instant;

public record WorkflowSummary(
        WorkflowId id, String name, String description, Instant lastExecutedAt, Instant updatedAt) {
}
