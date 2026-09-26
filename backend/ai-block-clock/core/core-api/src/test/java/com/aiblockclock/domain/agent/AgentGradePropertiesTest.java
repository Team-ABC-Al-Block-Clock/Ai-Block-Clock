package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgentGradePropertiesTest {
    // AI grading-formula.md v1.0 의 초기값이다.
    private static final AgentTierLadder LADDER = new AgentTierLadder(
            new AgentTierRequirement(new BigDecimal("80"), 0.98, 50),
            new AgentTierRequirement(new BigDecimal("65"), 0.95, 20),
            new AgentTierRequirement(new BigDecimal("45"), 0.90, 0));
    private static final AgentGradeProperties FORMULA = new AgentGradeProperties(
            10, 3.5, 1000, 2, new BigDecimal("0.6"), new BigDecimal("0.4"), LADDER);
    private static final AgentId AGENT = AgentId.newId();
    private static final Instant MEASURED_AT = Instant.parse("2026-09-25T00:00:00Z");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesTestConfiguration.class);

    // AI ai/demo/brand/prepared/seed_values_calculated.json 의 ag_brand_01 과 같은 재료다.
    // 보정 평점 (25 × 4.6 + 10 × 3.5) / 35 = 4.2857, 사용량 log10(121) / log10(1000) × 100 = 69.43, 성공률 120 / 125.
    @Test
    void 시연_시드_재료면_AI_검산과_같은_지표가_나온다() {
        AgentGradeIndicators indicators = FORMULA.indicatorsOf(metric(120, 5, 25, "4.6"));

        assertThat(indicators.adjustedRating()).isCloseTo(4.285714, within(1e-6));
        assertThat(indicators.ratingScore()).isCloseTo(82.142857, within(1e-6));
        assertThat(indicators.usageScore()).isCloseTo(69.426179, within(1e-6));
        assertThat(indicators.successRate()).isEqualTo(0.96);
        assertThat(indicators.successPenalty()).isCloseTo(0.9216, within(1e-12));
    }

    // AI 문서 5장의 검산이다. (0.6 × 82.14 + 0.4 × 69.43) × 0.9216 ≈ 71.0 → Gold 구간, 허들(0.95 · 20회)도 넘는다.
    @Test
    void 시연_시드_재료면_종합_점수_71점에_Tier_2다() {
        AgentGrade grade = FORMULA.gradeOf(metric(120, 5, 25, "4.6"));

        assertThat(grade.score()).isEqualByComparingTo("71.0150");
        assertThat(grade.scoreTier()).isEqualTo(Tier.TIER_2);
        assertThat(grade.tier()).isEqualTo(Tier.TIER_2);
    }

    @Test
    void 실행도_평가도_없는_신규_에이전트는_Tier_4다() {
        AgentGrade grade = FORMULA.gradeOf(AgentGradeMetric.fresh(AGENT));

        assertThat(grade.score()).isEqualByComparingTo("37.5");
        assertThat(grade.scoreTier()).isEqualTo(Tier.TIER_4);
        assertThat(grade.tier()).isEqualTo(Tier.TIER_4);
    }

    @Test
    void 종합_점수는_소수_넷째_자리까지다() {
        assertThat(FORMULA.gradeOf(metric(120, 5, 25, "4.6")).score().scale()).isEqualTo(4);
        assertThat(FORMULA.gradeOf(AgentGradeMetric.fresh(AGENT)).score().scale()).isEqualTo(4);
    }

    // 평점 · 사용량이 같고 성공률만 1 에서 0.5 가 되면, 더하는 식이면 조금 줄지만 곱하는 식이라 4 분의 1 이 된다.
    @Test
    void 성공률은_더하지_않고_곱해서_절반이면_점수가_4분의_1이_된다() {
        BigDecimal perfect = FORMULA.gradeOf(metric(999, 0, 0, null)).score();
        BigDecimal half = FORMULA.gradeOf(metric(999, 999, 0, null)).score();

        assertThat(perfect).isEqualByComparingTo("77.5");
        assertThat(half).isEqualByComparingTo("19.375");
    }

    // 평가가 없으면 보정 평점이 전체 평균(3.5)이라 평점 점수 62.5 를 받는다. 사용량이 가득 차면 77.5 로 Gold 구간이지만
    // Gold 허들(평가 20회)을 못 넘고, 평가 수 허들이 없는 Tier 3 에 머문다.
    @Test
    void 평가가_없어도_사용량이_많으면_평가_수_허들이_없는_Tier_3까지는_오른다() {
        AgentGrade grade = FORMULA.gradeOf(metric(999, 0, 0, null));

        assertThat(grade.scoreTier()).isEqualTo(Tier.TIER_2);
        assertThat(grade.tier()).isEqualTo(Tier.TIER_3);
    }

    // 980 / 1000 은 0.98 과 끝자리까지 같은 수다. 나눗셈 결과가 허들과 같을 때 떨어뜨리지 않는지 본다.
    @Test
    void 성공률이_허들과_딱_같으면_넘고_조금이라도_모자라면_한_칸_내려간다() {
        AgentGrade exact = FORMULA.gradeOf(metric(980, 20, 50, "5"));
        AgentGrade belowRate = FORMULA.gradeOf(metric(980, 21, 50, "5"));

        assertThat(exact.scoreTier()).isEqualTo(Tier.TIER_1);
        assertThat(exact.tier()).isEqualTo(Tier.TIER_1);
        assertThat(belowRate.scoreTier()).isEqualTo(Tier.TIER_1);
        assertThat(belowRate.tier()).isEqualTo(Tier.TIER_2);
    }

    @Test
    void 평가_수가_Tier_1_허들보다_하나_모자라면_Tier_2다() {
        assertThat(FORMULA.gradeOf(metric(980, 20, 50, "5")).tier()).isEqualTo(Tier.TIER_1);
        assertThat(FORMULA.gradeOf(metric(980, 20, 49, "5")).tier()).isEqualTo(Tier.TIER_2);
    }

    @Test
    void 실행도_평가도_없는_신규_에이전트는_전체_평균_평점에_사용량_0_성공률_1이다() {
        AgentGradeIndicators indicators = FORMULA.indicatorsOf(AgentGradeMetric.fresh(AGENT));

        assertThat(indicators.adjustedRating()).isEqualTo(3.5);
        assertThat(indicators.ratingScore()).isEqualTo(62.5);
        assertThat(indicators.usageScore()).isZero();
        assertThat(indicators.successRate()).isEqualTo(1);
        assertThat(indicators.successPenalty()).isEqualTo(1);
    }

    @Test
    void 평가가_하나뿐이면_5점이어도_전체_평균_쪽으로_당겨진다() {
        AgentGradeIndicators indicators = FORMULA.indicatorsOf(metric(0, 0, 1, "5"));

        assertThat(indicators.adjustedRating()).isCloseTo((5 + 10 * 3.5) / 11, within(1e-12));
        assertThat(indicators.adjustedRating()).isLessThan(3.7);
    }

    @Test
    void 평가가_많을수록_보정_평점이_실제_평균에_가까워진다() {
        double few = FORMULA.indicatorsOf(metric(0, 0, 5, "4.8")).adjustedRating();
        double many = FORMULA.indicatorsOf(metric(0, 0, 5000, "4.8")).adjustedRating();

        assertThat(few).isLessThan(many);
        assertThat(many).isCloseTo(4.8, within(0.01));
    }

    @Test
    void 사용량이_상한에_닿으면_100이고_넘어도_100에서_멈춘다() {
        assertThat(FORMULA.indicatorsOf(metric(999, 0, 0, null)).usageScore()).isEqualTo(100);
        assertThat(FORMULA.indicatorsOf(metric(1_000_000, 0, 0, null)).usageScore()).isEqualTo(100);
    }

    @Test
    void 사용량은_처음에_크게_오르고_갈수록_완만하다() {
        double first = FORMULA.indicatorsOf(metric(10, 0, 0, null)).usageScore()
                - FORMULA.indicatorsOf(metric(0, 0, 0, null)).usageScore();
        double later = FORMULA.indicatorsOf(metric(510, 0, 0, null)).usageScore()
                - FORMULA.indicatorsOf(metric(500, 0, 0, null)).usageScore();

        assertThat(first).isGreaterThan(later * 10);
    }

    @Test
    void 공급자_탓_실패만_있으면_성공률과_페널티가_0이다() {
        AgentGradeIndicators indicators = FORMULA.indicatorsOf(metric(0, 3, 0, null));

        assertThat(indicators.successRate()).isZero();
        assertThat(indicators.successPenalty()).isZero();
    }

    @Test
    void 성공률이_낮을수록_페널티가_제곱으로_커진다() {
        assertThat(FORMULA.indicatorsOf(metric(9, 1, 0, null)).successPenalty()).isCloseTo(0.81, within(1e-12));
        assertThat(FORMULA.indicatorsOf(metric(1, 1, 0, null)).successPenalty()).isCloseTo(0.25, within(1e-12));
    }

    @Test
    void 같은_재료면_몇_번을_계산해도_끝자리까지_같은_지표가_나온다() {
        AgentGradeMetric metric = metric(120, 5, 25, "4.6");
        AgentGradeIndicators first = FORMULA.indicatorsOf(metric);

        for (int i = 0; i < 100; i++) {
            assertThat(FORMULA.indicatorsOf(metric)).isEqualTo(first);
        }
    }

    @Test
    void 경계에_놓인_재료에서도_모든_지표가_정해진_범위를_벗어나지_않는다() {
        List<AgentGradeMetric> edges = List.of(
                AgentGradeMetric.fresh(AGENT),
                metric(0, 0, 1, "1"),
                metric(0, 0, 1, "5"),
                metric(0, 0, Integer.MAX_VALUE, "1"),
                metric(0, 0, Integer.MAX_VALUE, "5"),
                metric(Long.MAX_VALUE / 2, 0, 0, null),
                metric(0, Long.MAX_VALUE / 2, 0, null),
                metric(1, 0, 0, null));

        for (AgentGradeMetric edge : edges) {
            AgentGradeIndicators indicators = FORMULA.indicatorsOf(edge);

            assertThat(indicators.adjustedRating()).isBetween(1.0, 5.0);
            assertThat(indicators.ratingScore()).isBetween(0.0, 100.0);
            assertThat(indicators.usageScore()).isBetween(0.0, 100.0);
            assertThat(indicators.successRate()).isBetween(0.0, 1.0);
            assertThat(indicators.successPenalty()).isBetween(0.0, 1.0);
        }
    }

    @Test
    void 설정_파일의_조정값은_AI_수식_문서의_초기값이다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(AgentGradeProperties.class)).isEqualTo(FORMULA);
        });
    }

    @Test
    void 조정값이_범위를_벗어나면_기동하지_않는다() {
        List<String> invalid = List.of(
                "agent-grade.rating-prior-count=0",
                "agent-grade.rating-prior-mean=5.5",
                "agent-grade.rating-prior-mean=0.5",
                "agent-grade.usage-cap=1",
                "agent-grade.success-rate-exponent=0",
                "agent-grade.rating-weight=-0.1",
                "agent-grade.usage-weight=1.5",
                "agent-grade.tiers.tier-1.min-score=101",
                "agent-grade.tiers.tier-2.min-success-rate=1.5",
                "agent-grade.tiers.tier-3.min-review-count=-1");

        for (String property : invalid) {
            contextRunner.withPropertyValues(property)
                    .run(context -> assertThat(context).as(property).hasFailed());
        }
    }

    @Test
    void 두_가중치를_더해_1이_아니면_기동하지_않는다() {
        for (String property : List.of("agent-grade.rating-weight=0.7", "agent-grade.rating-weight=0.5")) {
            contextRunner.withPropertyValues(property)
                    .run(context -> assertThat(context.getStartupFailure()).as(property)
                            .hasStackTraceContaining("weightTotalOne"));
        }
    }

    // 0.7 + 0.3 은 double 로 더하면 1 이 되지 않을 수 있다. 가중치를 BigDecimal 로 받아 끝자리 없이 더하는지 본다.
    @Test
    void 두_가중치를_바꿔도_더해_1이면_기동한다() {
        contextRunner.withPropertyValues("agent-grade.rating-weight=0.7", "agent-grade.usage-weight=0.3")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void 높은_등급의_점수_구간이_낮은_등급보다_낮거나_같으면_기동하지_않는다() {
        List<String> invalid = List.of(
                "agent-grade.tiers.tier-1.min-score=60",
                "agent-grade.tiers.tier-1.min-score=65",
                "agent-grade.tiers.tier-3.min-score=70",
                "agent-grade.tiers.tier-3.min-score=0");

        for (String property : invalid) {
            contextRunner.withPropertyValues(property)
                    .run(context -> assertThat(context.getStartupFailure()).as(property)
                            .hasStackTraceContaining("scoreDescending"));
        }
    }

    private static AgentGradeMetric metric(long successes, long failures, int reviews, String averageRating) {
        return new AgentGradeMetric(AGENT, successes, failures, reviews,
                averageRating == null ? null : new BigDecimal(averageRating), MEASURED_AT);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AgentGradeProperties.class)
    static class PropertiesTestConfiguration {
    }
}
