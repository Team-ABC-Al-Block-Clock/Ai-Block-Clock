package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentGradeMetricEntity;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentGradeMetricReader {
    private final AgentGradeMetricRepository agentGradeMetricRepository;

    // 줄이 없는 에이전트는 아직 한 번도 세지 않은 신규다. 없는 것이 오류가 아니라 모두 0 인 재료로 읽는다.
    public AgentGradeMetric read(AgentId agent) {
        return agentGradeMetricRepository.findById(agent.value())
                .map(AgentGradeMetricReader::toDomain)
                .orElseGet(() -> AgentGradeMetric.fresh(agent));
    }

    private static AgentGradeMetric toDomain(AgentGradeMetricEntity entity) {
        return new AgentGradeMetric(
                AgentId.of(entity.getAgentId()),
                entity.getSuccessfulRuns(),
                entity.getSupplierFailures(),
                entity.getReviewCount(),
                entity.getAverageRating(),
                entity.getMeasuredAt());
    }
}
