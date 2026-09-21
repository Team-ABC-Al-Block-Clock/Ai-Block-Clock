package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class NodeDefinition {
    private final NodeDefinitionId id;
    private final OwnerId owner;
    private final Timestamps timestamps;

    private NodeSpec spec;
    private Prompt prompt;

    public static NodeDefinition create(NodeDefinitionId id, OwnerId owner, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, Timestamps.unsaved(), spec, prompt);
    }

    public static NodeDefinition restore(
            NodeDefinitionId id, OwnerId owner, Timestamps timestamps, NodeSpec spec, Prompt prompt) {
        return new NodeDefinition(id, owner, timestamps, spec, prompt);
    }

    public void update(OwnerId requester, NodeSpec spec, Prompt prompt) {
        ensureOwner(requester);

        this.spec = spec;
        this.prompt = prompt;
    }

    public boolean isOwnedBy(OwnerId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(OwnerId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.NODE_FORBIDDEN);
        }
    }
}
