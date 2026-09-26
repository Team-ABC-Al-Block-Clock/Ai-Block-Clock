package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;

import java.math.BigDecimal;

// 에이전트 하나에 매긴 종합 점수와 등급이다 (PAY-019/020). 어떤 재료 · 지표로 매겼는지를 함께 들고 다녀 그대로 이력에 남긴다.
//   score      (평점 가중치 × 평점 점수 + 사용량 가중치 × 사용량 점수) × 성공률 페널티. 0 ~ 100, 소수 넷째 자리까지
//   scoreTier  점수만 보고 닿은 등급
//   tier       허들까지 본 확정 등급. 허들에 걸리면 scoreTier 보다 낮다
public record AgentGrade(
        AgentGradeMetric metric,
        AgentGradeIndicators indicators,
        BigDecimal score,
        Tier scoreTier,
        Tier tier
) {
}
