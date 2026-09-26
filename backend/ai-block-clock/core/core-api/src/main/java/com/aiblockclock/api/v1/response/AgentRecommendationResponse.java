package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.PublicAgent;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "자연어로 찾은 에이전트. 점수가 높은 순이다")
public record AgentRecommendationResponse(
        @Schema(description = "추천된 에이전트")
        List<RecommendedAgentResponse> items
) {
    public static AgentRecommendationResponse from(List<PublicAgent> agents) {
        return new AgentRecommendationResponse(agents.stream()
                .map(RecommendedAgentResponse::from)
                .toList());
    }

    @Schema(description = "추천된 에이전트 하나")
    public record RecommendedAgentResponse(
            @Schema(description = "에이전트 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
            String agentId,

            @Schema(description = "에이전트 이름", example = "성적 분석 에이전트")
            String name,

            @Schema(description = "한 줄 소개", example = "성적표를 받아 학생별 리포트를 쓴다")
            String summary,

            @Schema(description = "실행 1회당 단가. 소수점 오차를 막기 위해 문자열이다", example = "6.000000")
            String price
    ) {
        static RecommendedAgentResponse from(PublicAgent agent) {
            return new RecommendedAgentResponse(
                    agent.id().value().toString(),
                    agent.name(),
                    agent.summary(),
                    agent.price().value().toPlainString());
        }
    }
}
