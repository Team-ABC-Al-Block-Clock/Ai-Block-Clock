package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.agent.Tier;

// 재산정(PAY-022)으로 버전 하나의 가격 등급이 바뀐 것이다. 바뀌기 전 · 뒤 판매가는 원가에 각 등급의 배율을 곱해 낸다.
public record AgentTierChange(AgentId agent, AgentVersionId version, AbcAmount baseCost, Tier before, Tier after) {
    public AbcAmount beforePrice() {
        return baseCost.multiply(before.priceMultiplier());
    }

    public AbcAmount afterPrice() {
        return baseCost.multiply(after.priceMultiplier());
    }
}
