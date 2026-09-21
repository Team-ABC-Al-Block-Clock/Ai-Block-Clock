package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeDefinitionDeletionValidator {
    private final NodeDefinitionUsageReader usageReader;
    private final NodeDefinitionWriteValidator writeValidator;

    public void ensureDeletable(NodeDefinitionEntity entity, OwnerId requester) {
        writeValidator.ensureOwner(entity, requester);

        if (usageReader.isPlacedInAnyWorkflow(NodeDefinitionId.of(entity.getId()))) {
            throw new CoreException(ErrorType.NODE_PLACED_IN_WORKFLOW);
        }
    }
}
