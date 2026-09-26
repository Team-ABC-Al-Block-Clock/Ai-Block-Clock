package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentGradeHistoryEntity;
import com.aiblockclock.storage.agent.AgentGradeHistoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class AgentGradeHistoryWriter {
    // 지표는 소수 여섯째 자리까지 남긴다. 점수 · 허들 판정은 반올림 전 지표로 이미 끝났고, 이 칸은 다시 보려고 남기는 것이다.
    private static final int INDICATOR_SCALE = 6;

    private final AgentGradeHistoryRepository agentGradeHistoryRepository;

    // 마지막 줄과 재료 · 점수 · 등급이 모두 같으면 새 줄을 더하지 않는다. 배치를 몇 번 돌려도 같은 결과가 쌓이지 않게 하려는 것이다.
    // 조정값이 바뀌어 점수가 달라지면 재료가 같아도 새 줄이다. 돌려주는 것은 이 결과를 담은 줄(새 줄이거나 마지막 줄)의 ID 다.
    public long appendIfChanged(AgentGrade grade) {
        return agentGradeHistoryRepository.findFirstByAgentIdOrderByIdDesc(grade.metric().agent().value())
                .filter(last -> isSame(last, grade))
                .orElseGet(() -> agentGradeHistoryRepository.save(toEntity(grade)))
                .getId();
    }

    private static boolean isSame(AgentGradeHistoryEntity last, AgentGrade grade) {
        AgentGradeMetric metric = grade.metric();

        return last.getSuccessfulRuns() == metric.successfulRuns()
                && last.getSupplierFailures() == metric.supplierFailures()
                && last.getReviewCount() == metric.reviewCount()
                && isSameRating(last.getAverageRating(), metric.averageRating())
                && Objects.equals(last.getMeasuredAt(), metric.measuredAt())
                && last.getScore().compareTo(grade.score()) == 0
                && last.getScoreTier() == grade.scoreTier()
                && last.getTier() == grade.tier();
    }

    // 평점은 DB 에서 소수 둘째 자리로 읽혀 4.6 과 4.60 처럼 자릿수가 다를 수 있어 값으로 비교한다. 평가가 없으면 둘 다 비어 있다.
    private static boolean isSameRating(BigDecimal recorded, BigDecimal current) {
        if (recorded == null || current == null) {
            return recorded == current;
        }

        return recorded.compareTo(current) == 0;
    }

    private static AgentGradeHistoryEntity toEntity(AgentGrade grade) {
        AgentGradeMetric metric = grade.metric();
        AgentGradeIndicators indicators = grade.indicators();

        return new AgentGradeHistoryEntity(
                metric.agent().value(),
                metric.successfulRuns(),
                metric.supplierFailures(),
                metric.reviewCount(),
                metric.averageRating(),
                metric.measuredAt(),
                toIndicator(indicators.ratingScore()),
                toIndicator(indicators.usageScore()),
                toIndicator(indicators.successRate()),
                grade.score(),
                grade.scoreTier(),
                grade.tier());
    }

    private static BigDecimal toIndicator(double value) {
        return BigDecimal.valueOf(value).setScale(INDICATOR_SCALE, RoundingMode.HALF_UP);
    }
}
