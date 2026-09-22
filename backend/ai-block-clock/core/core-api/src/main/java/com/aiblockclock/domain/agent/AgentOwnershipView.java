package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;

public interface AgentOwnershipView {
    boolean isOwnedBy(AgentId agentId, UserId owner);
}
