package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;

import java.time.Instant;

public record FavoriteAgent(
        AgentId agentId,
        String name,
        String summary,
        String iconUrl,
        AbcAmount price,
        long executionCount,
        String ownerNickname,
        Instant favoritedAt
) {
}
