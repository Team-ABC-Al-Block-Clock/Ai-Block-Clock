package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.Tier;

import java.math.BigDecimal;

public record PublicAgent(AgentId id, UserId owner, String name, String summary, Tier tier, AbcAmount baseCost) {
    public AbcAmount price() {
        return baseCost.multiply(BigDecimal.ONE.add(tier.premiumRate()));
    }
}
