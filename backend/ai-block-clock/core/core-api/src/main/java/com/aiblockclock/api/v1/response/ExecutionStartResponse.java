package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.enums.execution.ExecutionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "실행 접수 결과")
public record ExecutionStartResponse(
        @Schema(description = "실행 ID. 진행 상황을 이 값으로 조회한다",
                example = "8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String executionId,

        @Schema(description = "접수 시점의 상태")
        ExecutionStatus status,

        @Schema(description = "펼친 프롬프트 수. 에이전트는 안의 단계 수만큼 센다", example = "3")
        int stepCount
) {
    public static ExecutionStartResponse from(Execution execution) {
        return new ExecutionStartResponse(
                execution.getId().value().toString(), execution.getStatus(), execution.getPromptCount());
    }
}
