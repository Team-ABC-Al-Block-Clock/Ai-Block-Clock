package com.aiblockclock.domain.node;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class NodeDefinition {
    private final NodeDefinitionId id;
    private final UserId owner;

    private String name;
    private Prompt prompt;
    private AiModel model;
    private AbcAmount baseCost;

    public static NodeDefinition create(
            NodeDefinitionId id, UserId owner, String name, Prompt prompt, AiModel model, AbcAmount baseCost) {
        return new NodeDefinition(id, owner, name, prompt, model, baseCost);
    }

    public void update(
            UserId requester, String name, Prompt prompt, AiModel model, AbcAmount baseCost) {
        ensureOwner(requester);

        this.name = name;
        this.prompt = prompt;
        this.model = model;
        this.baseCost = baseCost;
    }

    public boolean isOwnedBy(UserId candidate) {
        return owner.equals(candidate);
    }

    public void ensureOwner(UserId requester) {
        if (!isOwnedBy(requester)) {
            throw new CoreException(ErrorType.NODE_FORBIDDEN);
        }
    }
}
