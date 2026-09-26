package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 등급(PAY-016 ~ 020) 수식의 조정값과 그 수식이다. 값은 AI grading-formula.md v1.0 의 초기값이고,
// 팀 합의로 바뀔 수 있어 코드가 아니라 설정에 둔다. 범위를 벗어난 값이면 기동할 때 막는다.
//   ratingPriorCount     보정 강도. 모든 에이전트가 이만큼의 가상 평가를 받고 시작한다고 본다 (m)
//   ratingPriorMean      가상 평가의 점수. 전체 평균 평점이다 (C)
//   usageCap             정규화 사용량이 100 이 되는 누적 성공 실행 수 (U_max)
//   successRateExponent  성공률 페널티의 지수 (k)
//   ratingWeight         종합 점수에서 평점 점수의 가중치 (W1)
//   usageWeight          종합 점수에서 사용량 점수의 가중치 (W2). W1 과 더해 1 이어야 점수가 0 ~ 100 에 놓인다
//   tiers                등급마다 점수 구간과 허들
// 로그 · 거듭제곱은 StrictMath 로 계산한다. Math 는 JVM 마다 끝자리가 다를 수 있어 같은 재료에서 다른 지표가 나올 수 있다.
@Validated
@ConfigurationProperties(prefix = "agent-grade")
public record AgentGradeProperties(
        @Positive int ratingPriorCount,
        @DecimalMin("1") @DecimalMax("5") double ratingPriorMean,
        @Min(2) long usageCap,
        @Positive double successRateExponent,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal ratingWeight,
        @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal usageWeight,
        @Valid @NotNull AgentTierLadder tiers
) {
    private static final double LOWEST_RATING = 1;
    private static final double RATING_RANGE = 4;
    private static final double FULL_SCORE = 100;
    private static final int SCORE_SCALE = 4;

    public AgentGradeIndicators indicatorsOf(AgentGradeMetric metric) {
        double adjustedRating = adjustedRatingOf(metric);
        double successRate = successRateOf(metric);

        return new AgentGradeIndicators(
                adjustedRating,
                (adjustedRating - LOWEST_RATING) / RATING_RANGE * FULL_SCORE,
                usageScoreOf(metric),
                successRate,
                StrictMath.pow(successRate, successRateExponent));
    }

    // 점수로 후보 등급을 잡고, 허들(성공률 · 평가 수)을 넘지 못하면 넘는 등급까지 내린다.
    public AgentGrade gradeOf(AgentGradeMetric metric) {
        AgentGradeIndicators indicators = indicatorsOf(metric);
        BigDecimal score = scoreOf(indicators);
        Tier scoreTier = tiers.scoreTierOf(score);

        return new AgentGrade(metric, indicators, score, scoreTier,
                tiers.clearedTierOf(scoreTier, indicators.successRate(), metric.reviewCount()));
    }

    // 두 가중치를 더해 1 이 아니면 점수가 0 ~ 100 을 벗어나거나 모자라서, 등급 구간(80 · 65 · 45)이 뜻을 잃는다.
    // 빠진 값은 @NotNull 이 따로 막는다.
    @AssertTrue
    public boolean isWeightTotalOne() {
        if (ratingWeight == null || usageWeight == null) {
            return true;
        }

        return ratingWeight.add(usageWeight).compareTo(BigDecimal.ONE) == 0;
    }

    // 평가가 적으면 전체 평균 쪽으로 당긴다. 5점 하나뿐인 신규 에이전트가 맨 위로 뛰어오르지 못하게 하려는 것이다.
    // 평가가 없으면 전체 평균 그대로다.
    private double adjustedRatingOf(AgentGradeMetric metric) {
        double reviewed = metric.hasReviews()
                ? metric.reviewCount() * metric.averageRating().doubleValue()
                : 0;

        return (reviewed + ratingPriorCount * ratingPriorMean) / ((double) metric.reviewCount() + ratingPriorCount);
    }

    // 사용량은 처음에 크게 오르고 갈수록 완만해지도록 로그 눈금에 놓는다. 상한을 넘으면 100 에서 멈춘다.
    private double usageScoreOf(AgentGradeMetric metric) {
        double score = StrictMath.log10(metric.successfulRuns() + 1) / StrictMath.log10(usageCap) * FULL_SCORE;

        return Math.min(score, FULL_SCORE);
    }

    // 센 실행이 없으면 1 이다. 신규 에이전트를 실패한 적 없는 것으로 보고, 성공률 허들로 떨어뜨리지 않는다.
    private static double successRateOf(AgentGradeMetric metric) {
        if (metric.countedRuns() == 0) {
            return 1;
        }

        return (double) metric.successfulRuns() / metric.countedRuns();
    }

    // 성공률은 더하지 않고 곱한다. 성공률이 낮으면 평점 · 사용량이 높아도 점수가 크게 깎인다.
    // 소수 넷째 자리에서 반올림한 값으로 등급을 매긴다. 이력에 남은 점수로 다시 매겨도 같은 등급이 나오게 하려는 것이다.
    private BigDecimal scoreOf(AgentGradeIndicators indicators) {
        double weighted = ratingWeight.doubleValue() * indicators.ratingScore()
                + usageWeight.doubleValue() * indicators.usageScore();

        return BigDecimal.valueOf(weighted * indicators.successPenalty()).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
