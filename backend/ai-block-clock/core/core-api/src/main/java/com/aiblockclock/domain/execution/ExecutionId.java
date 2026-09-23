package com.aiblockclock.domain.execution;

import java.util.UUID;

public record ExecutionId(UUID value) {
    public static ExecutionId of(UUID value) {
        return new ExecutionId(value);
    }

    public static ExecutionId of(String value) {
        return new ExecutionId(UUID.fromString(value));
    }

    public static ExecutionId newId() {
        return new ExecutionId(UUID.randomUUID());
    }
}
