package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.user.UserId;

public interface NodeDefinitionOwnershipView {
    boolean isOwnedBy(NodeDefinitionId nodeId, UserId owner);
}
