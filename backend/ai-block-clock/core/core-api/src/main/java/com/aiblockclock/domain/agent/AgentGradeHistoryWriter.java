package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentGradeHistoryEntity;
import com.aiblockclock.storage.agent.AgentGradeHistoryRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@RequiredArgsConstructor
public class AgentGradeHistoryWriter {
    // 지표는 소수 여섯째 자리까지 남긴다. 점수 · 허들 판정은 반올림 전 지표로 이미 끝났고, 이 칸은 다시 보려고 남기는 것이다.
    private static final int INDICATOR_SCALE = 6;

    private final AgentGradeHistoryRepository agentGradeHistoryRepository;

    public void append(AgentGrade grade) {
        agentGradeHistoryRepository.save(toEntity(grade));
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
