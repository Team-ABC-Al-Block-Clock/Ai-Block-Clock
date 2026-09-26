package com.aiblockclock.domain.agent;

import java.util.UUID;

public record AgentId(UUID value) {
    public static AgentId of(UUID value) {
        return new AgentId(value);
    }

    public static AgentId of(String value) {
        return new AgentId(UUID.fromString(value));
    }

    public static AgentId newId() {
        return new AgentId(UUID.randomUUID());
    }

}
