package com.aiblockclock.domain.node;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

@Component
public class NodeDefinitionWriteValidator {
    public void ensureOwner(NodeDefinitionEntity entity, OwnerId requester) {
        if (!OwnerId.of(entity.getOwnerId()).equals(requester)) {
            throw new CoreException(ErrorType.NODE_FORBIDDEN);
        }
    }
}
