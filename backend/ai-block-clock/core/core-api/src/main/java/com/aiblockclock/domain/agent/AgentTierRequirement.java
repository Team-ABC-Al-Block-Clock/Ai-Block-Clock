package com.aiblockclock.domain.agent;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

// 한 등급의 조건이다. 점수가 minScore 에 닿으면 그 등급의 후보가 되고, 허들(성공률 · 평가 수)까지 넘어야 그 등급이 된다 (PAY-020).
public record AgentTierRequirement(
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal minScore,
        @DecimalMin("0") @DecimalMax("1") double minSuccessRate,
        @PositiveOrZero int minReviewCount
) {
    // 가장 낮은 등급의 조건이다. 누구나 닿고 누구나 넘는다. 신규 에이전트가 머무는 자리다.
    static final AgentTierRequirement NONE = new AgentTierRequirement(BigDecimal.ZERO, 0, 0);

    public boolean isReachedBy(BigDecimal score) {
        return score.compareTo(minScore) >= 0;
    }

    public boolean isClearedBy(double successRate, int reviewCount) {
        return successRate >= minSuccessRate && reviewCount >= minReviewCount;
    }
}
