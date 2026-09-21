package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;

public interface AgentOwnershipView {
    boolean isOwnedBy(AgentId agentId, OwnerId owner);
}
