package com.aiblockclock.domain.node;

import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.domain.user.UserId;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NodeDefinition {
    private final NodeDefinitionId id;
    private final UserId owner;
    private final Timestamps timestamps;
    private final NodeSpec spec;
    private final Prompt prompt;

    public static NodeDefinition create(NodeDefinitionId id, UserId owner, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, Timestamps.unsaved(), spec, prompt);
    }

    public static NodeDefinition restore(
            NodeDefinitionId id, UserId owner, Timestamps timestamps, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, timestamps, spec, prompt);
    }

    public boolean isOwnedBy(UserId candidate) {
        return owner.equals(candidate);
    }
}
