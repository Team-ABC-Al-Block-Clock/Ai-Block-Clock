package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class NodeDefinitionDeletionPolicy {
    private final NodeDefinitionUsageView usageView;

    public void ensureDeletable(NodeDefinition node, OwnerId requester) {
        node.ensureOwner(requester);

        if (usageView.isPlacedInAnyWorkflow(node.getId())) {
            throw new CoreException(ErrorType.NODE_PLACED_IN_WORKFLOW);
        }
    }
}
