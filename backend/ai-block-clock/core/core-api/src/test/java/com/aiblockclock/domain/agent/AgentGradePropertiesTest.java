package com.aiblockclock.domain.agent;

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
    private static final AgentGradeProperties FORMULA = new AgentGradeProperties(10, 3.5, 1000, 2);
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

    // 종합 점수는 다음 작업(PAY-019)이 만든다. 여기서는 지표를 AI 문서의 가중치(0.6 · 0.4)에 넣어 71.0 이 되는지만 본다.
    @Test
    void 시연_시드_지표를_AI_문서의_가중치에_넣으면_종합_점수가_71이_된다() {
        AgentGradeIndicators indicators = FORMULA.indicatorsOf(metric(120, 5, 25, "4.6"));

        double score = (0.6 * indicators.ratingScore() + 0.4 * indicators.usageScore()) * indicators.successPenalty();

        assertThat(score).isCloseTo(71.015, within(0.001));
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
                "agent-grade.success-rate-exponent=0");

        for (String property : invalid) {
            contextRunner.withPropertyValues(property)
                    .run(context -> assertThat(context).as(property).hasFailed());
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
