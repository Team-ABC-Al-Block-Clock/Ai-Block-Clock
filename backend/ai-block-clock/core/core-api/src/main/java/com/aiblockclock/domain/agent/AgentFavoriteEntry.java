package com.aiblockclock.domain.agent;

import java.time.Instant;

public record AgentFavoriteEntry(AgentId agentId, Instant favoritedAt) {
}
