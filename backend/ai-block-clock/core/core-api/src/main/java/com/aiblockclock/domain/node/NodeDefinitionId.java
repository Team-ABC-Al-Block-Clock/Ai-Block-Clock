package com.aiblockclock.domain.node;

import java.util.UUID;

public record NodeDefinitionId(UUID value) {
    public static NodeDefinitionId of(UUID value) {
        return new NodeDefinitionId(value);
    }

    public static NodeDefinitionId of(String value) {
        return new NodeDefinitionId(UUID.fromString(value));
    }

    public static NodeDefinitionId newId() {
        return new NodeDefinitionId(UUID.randomUUID());
    }

}
