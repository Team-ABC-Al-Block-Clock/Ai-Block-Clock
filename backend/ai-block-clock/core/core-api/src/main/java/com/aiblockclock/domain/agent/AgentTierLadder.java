package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Arrays;

// 절대평가 등급표다 (PAY-020). Tier 1 ~ 3 에 조건이 있고 Tier 4 는 조건이 없다.
// 점수로 닿은 가장 높은 등급을 후보로 잡고, 그 등급의 허들을 넘지 못하면 넘는 등급까지 한 칸씩 내려간다.
public record AgentTierLadder(
        @Valid @NotNull AgentTierRequirement tier1,
        @Valid @NotNull AgentTierRequirement tier2,
        @Valid @NotNull AgentTierRequirement tier3
) {
    // 점수만 보고 닿은 가장 높은 등급이다. 어느 구간에도 닿지 못하면 Tier 4 다.
    public Tier scoreTierOf(BigDecimal score) {
        return Arrays.stream(Tier.values())
                .filter(tier -> requirementOf(tier).isReachedBy(score))
                .findFirst()
                .orElseThrow();
    }

    // 후보 등급부터 내려가며 허들을 넘는 첫 등급이다. 위로는 올라가지 않는다. Tier 4 는 누구나 넘으므로 늘 찾는다.
    // 아래 등급의 점수 구간은 늘 더 낮아서(isScoreDescending) 내려간 등급의 점수는 따로 보지 않는다.
    public Tier clearedTierOf(Tier scoreTier, double successRate, int reviewCount) {
        return Arrays.stream(Tier.values())
                .filter(tier -> tier.level() >= scoreTier.level())
                .filter(tier -> requirementOf(tier).isClearedBy(successRate, reviewCount))
                .findFirst()
                .orElseThrow();
    }

    // 등급이 높을수록 점수 구간의 아래 끝도 높아야 한다. 뒤바뀌거나 같으면 어느 점수로도 닿지 못하는 등급이 생긴다.
    // 빠진 값은 @NotNull 이 따로 막는다.
    @AssertTrue
    public boolean isScoreDescending() {
        if (tier1 == null || tier2 == null || tier3 == null
                || tier1.minScore() == null || tier2.minScore() == null || tier3.minScore() == null) {
            return true;
        }

        return tier1.minScore().compareTo(tier2.minScore()) > 0
                && tier2.minScore().compareTo(tier3.minScore()) > 0
                && tier3.minScore().compareTo(AgentTierRequirement.NONE.minScore()) > 0;
    }

    private AgentTierRequirement requirementOf(Tier tier) {
        return switch (tier) {
            case TIER_1 -> tier1;
            case TIER_2 -> tier2;
            case TIER_3 -> tier3;
            case TIER_4 -> AgentTierRequirement.NONE;
        };
    }
}
