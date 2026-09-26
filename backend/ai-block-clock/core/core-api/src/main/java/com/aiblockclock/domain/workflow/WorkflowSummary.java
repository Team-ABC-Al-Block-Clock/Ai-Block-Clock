package com.aiblockclock.domain.workflow;

import java.time.Instant;

public record WorkflowSummary(WorkflowId id, String name, Instant lastExecutedAt, Instant updatedAt) {
}
