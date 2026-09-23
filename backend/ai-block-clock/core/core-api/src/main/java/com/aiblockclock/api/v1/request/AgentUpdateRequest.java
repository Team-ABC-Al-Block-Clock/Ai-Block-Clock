package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.agent.AgentProfile;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "에이전트 수정 요청")
public record AgentUpdateRequest(
        @Schema(description = "새로 굳힐 워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        @NotNull UUID workflowId,

        @Schema(description = "사용자가 보던 회차. 그 사이 워크플로우가 바뀌었으면 거부한다", example = "2")
        @NotNull @Positive Long revision,

        @Schema(description = "에이전트 이름", example = "성적 분석 에이전트", minLength = 2, maxLength = 40)
        @NotBlank @Size(min = 2, max = 40) String name,

        @Schema(description = "한 줄 소개", example = "성적표를 받아 학생별 리포트를 쓴다", maxLength = 80)
        @NotBlank @Size(max = 80) String summary,

        @Schema(description = "상세 설명", example = "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                maxLength = 2000)
        @NotBlank @Size(max = 2000) String description,

        @Schema(description = "사용 예시", example = "성적표 엑셀을 올리면 분석 리포트가 나옵니다",
                maxLength = 500, nullable = true)
        @Size(max = 500) String usageExample,

        @Schema(description = "첫 화면에 띄울 인사말", example = "무엇을 분석할까요?",
                maxLength = 20, nullable = true)
        @Size(max = 20) String onboardingMessage
) {
    public AgentProfile toProfile() {
        return new AgentProfile(name, summary, description, usageExample, onboardingMessage, null);
    }
}
