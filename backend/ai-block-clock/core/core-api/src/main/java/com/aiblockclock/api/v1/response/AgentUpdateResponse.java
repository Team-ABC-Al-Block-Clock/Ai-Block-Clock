package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.AgentRegistration;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "에이전트 수정 결과")
public record AgentUpdateResponse(
        @Schema(description = "에이전트 ID", example = "9f2c1f0e-6a3d-4f21-9c2a-8b1d5e7f0a44")
        String agentId,

        @Schema(description = "수정된 이름", example = "성적 분석 에이전트")
        String name,

        @Schema(description = "지금 공개된 버전 번호. 굳힌 원본이 그대로면 오르지 않는다", example = "2")
        int versionNo,

        @Schema(description = "실행 단가. 소수점 오차를 막기 위해 문자열로 내린다", example = "6.000000")
        String price
) {
    public static AgentUpdateResponse from(AgentRegistration registration) {
        return new AgentUpdateResponse(
                registration.agent().getId().value().toString(),
                registration.agent().getProfile().name(),
                registration.version().getNumber().value(),
                registration.version().finalPrice().value().toPlainString());
    }
}
