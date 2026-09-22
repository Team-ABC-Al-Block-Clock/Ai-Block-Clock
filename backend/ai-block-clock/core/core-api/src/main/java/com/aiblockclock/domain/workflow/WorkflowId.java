package com.aiblockclock.domain.workflow;

import java.util.UUID;

public record WorkflowId(UUID value) {
    public static WorkflowId of(UUID value) {
        return new WorkflowId(value);
    }

    public static WorkflowId of(String value) {
        return new WorkflowId(UUID.fromString(value));
    }

    public static WorkflowId newId() {
        return new WorkflowId(UUID.randomUUID());
    }

}
