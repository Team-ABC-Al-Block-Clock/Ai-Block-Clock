package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;

import java.time.Instant;

public record MarketplaceAgent(AgentId id, UserId owner, AgentProfile profile, AbcAmount price, Instant createdAt) {
}
