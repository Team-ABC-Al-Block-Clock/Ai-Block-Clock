package com.aiblockclock.domain.agent;

import java.util.UUID;

public record AgentVersionId(UUID value) {
    public static AgentVersionId of(UUID value) {
        return new AgentVersionId(value);
    }

    public static AgentVersionId of(String value) {
        return new AgentVersionId(UUID.fromString(value));
    }

    public static AgentVersionId newId() {
        return new AgentVersionId(UUID.randomUUID());
    }

}
