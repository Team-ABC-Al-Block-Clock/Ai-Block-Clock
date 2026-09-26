package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTierLadderTest {
    // AI grading-formula.md v1.0 3장의 절대평가 구간과 허들이다.
    private static final AgentTierLadder LADDER = new AgentTierLadder(
            new AgentTierRequirement(new BigDecimal("80"), 0.98, 50),
            new AgentTierRequirement(new BigDecimal("65"), 0.95, 20),
            new AgentTierRequirement(new BigDecimal("45"), 0.90, 0));

    @Test
    void 점수가_구간의_아래_끝과_같으면_그_등급에_닿는다() {
        Map<String, Tier> edges = Map.of(
                "100", Tier.TIER_1,
                "80", Tier.TIER_1,
                "65", Tier.TIER_2,
                "45", Tier.TIER_3);

        edges.forEach((score, tier) ->
                assertThat(LADDER.scoreTierOf(new BigDecimal(score))).as(score).isEqualTo(tier));
    }

    @Test
    void 점수가_구간의_아래_끝보다_조금이라도_낮으면_한_칸_아래다() {
        Map<String, Tier> edges = Map.of(
                "79.9999", Tier.TIER_2,
                "64.9999", Tier.TIER_3,
                "44.9999", Tier.TIER_4,
                "0", Tier.TIER_4);

        edges.forEach((score, tier) ->
                assertThat(LADDER.scoreTierOf(new BigDecimal(score))).as(score).isEqualTo(tier));
    }

    @Test
    void 허들과_딱_같으면_넘는다() {
        assertThat(LADDER.clearedTierOf(Tier.TIER_1, 0.98, 50)).isEqualTo(Tier.TIER_1);
        assertThat(LADDER.clearedTierOf(Tier.TIER_2, 0.95, 20)).isEqualTo(Tier.TIER_2);
        assertThat(LADDER.clearedTierOf(Tier.TIER_3, 0.90, 0)).isEqualTo(Tier.TIER_3);
    }

    // AI 문서 3장의 예다. Score 82(Platinum 구간)인데 평가 30회뿐이면 Platinum 허들(50회)을 못 넘고 Gold 허들(20회)은 넘는다.
    @Test
    void 허들을_못_넘으면_허들을_넘는_가장_높은_등급으로_내려간다() {
        assertThat(LADDER.clearedTierOf(Tier.TIER_1, 0.99, 30)).isEqualTo(Tier.TIER_2);
        assertThat(LADDER.clearedTierOf(Tier.TIER_1, 0.97, 100)).isEqualTo(Tier.TIER_2);
    }

    @Test
    void 여러_등급의_허들을_못_넘으면_그만큼_더_내려간다() {
        assertThat(LADDER.clearedTierOf(Tier.TIER_1, 0.99, 10)).isEqualTo(Tier.TIER_3);
        assertThat(LADDER.clearedTierOf(Tier.TIER_1, 0.89, 100)).isEqualTo(Tier.TIER_4);
    }

    // 허들은 깎기만 한다. 점수가 닿지 못한 등급으로 끌어올리지 않는다.
    @Test
    void 허들을_넉넉히_넘어도_점수로_닿은_등급보다_올라가지_않는다() {
        assertThat(LADDER.clearedTierOf(Tier.TIER_3, 1.0, 1000)).isEqualTo(Tier.TIER_3);
        assertThat(LADDER.clearedTierOf(Tier.TIER_4, 1.0, 1000)).isEqualTo(Tier.TIER_4);
    }

    @Test
    void Tier_4는_허들이_없어_성공률이_0이어도_머문다() {
        assertThat(LADDER.clearedTierOf(Tier.TIER_4, 0, 0)).isEqualTo(Tier.TIER_4);
    }
}
