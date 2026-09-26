package com.aiblockclock.domain.node;

import java.util.UUID;

public record NodeTestRunId(UUID value) {
    public static NodeTestRunId of(UUID value) {
        return new NodeTestRunId(value);
    }

    public static NodeTestRunId newId() {
        return new NodeTestRunId(UUID.randomUUID());
    }
}
