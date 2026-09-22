package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NodeDefinition {
    private final NodeDefinitionId id;
    private final OwnerId owner;
    private final Timestamps timestamps;
    private final NodeSpec spec;
    private final Prompt prompt;

    public static NodeDefinition create(NodeDefinitionId id, OwnerId owner, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, Timestamps.unsaved(), spec, prompt);
    }

    public static NodeDefinition restore(
            NodeDefinitionId id, OwnerId owner, Timestamps timestamps, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, timestamps, spec, prompt);
    }
}
