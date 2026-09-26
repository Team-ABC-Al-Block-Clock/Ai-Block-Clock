package com.aiblockclock.domain.agent;

import java.math.BigDecimal;
import java.time.Instant;

// 에이전트 하나의 등급 재료다. 지표(AgentGradeIndicators)는 이 재료에서 계산한다.
// 성공 수 · 공급자 탓 실패 수는 실행을 닫을 때 쌓인다. 평가 수 · 평균 평점은 리뷰 기능이 없어 시연 시드로만 들어온다.
// averageRating 은 평가가 없으면 비어 있다. measuredAt 은 재료가 마지막으로 바뀐 때이고, 한 번도 세지 않았으면 비어 있다.
public record AgentGradeMetric(
        AgentId agent,
        long successfulRuns,
        long supplierFailures,
        int reviewCount,
        BigDecimal averageRating,
        Instant measuredAt
) {
    // 아직 한 번도 세지 않은 신규 에이전트다. 실행도 평가도 없다.
    public static AgentGradeMetric fresh(AgentId agent) {
        return new AgentGradeMetric(agent, 0, 0, 0, null, null);
    }

    public boolean hasReviews() {
        return reviewCount > 0;
    }

    // 성공률의 분모다. 플랫폼 · 수요자 탓 실패는 애초에 세지 않으므로 여기 들어오지 않는다.
    public long countedRuns() {
        return successfulRuns + supplierFailures;
    }
}
