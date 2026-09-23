package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.agent.Tier;

import java.math.BigDecimal;
import java.util.UUID;

public record PublicAgentRow(
        UUID agentId,
        Long ownerId,
        String name,
        String summary,
        Tier tier,
        BigDecimal baseCost
) {
}
