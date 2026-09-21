package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.node.NodeDefinitionId;

public interface NodeDefinitionOwnershipView {
    boolean isOwnedBy(NodeDefinitionId nodeId, OwnerId owner);
}
