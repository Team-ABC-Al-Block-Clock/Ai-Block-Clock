package com.aiblockclock.api.v1.response;

import java.time.Instant;
import java.util.UUID;

public record WorkflowDeleteResponse(String workflowId, Instant deletedAt) {
    public static WorkflowDeleteResponse of(UUID workflowId, Instant deletedAt) {
        return new WorkflowDeleteResponse(workflowId.toString(), deletedAt);
    }
}
