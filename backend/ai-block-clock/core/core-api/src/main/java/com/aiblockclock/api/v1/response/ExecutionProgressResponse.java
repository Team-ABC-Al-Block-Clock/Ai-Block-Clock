package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.execution.ExecutionProgress;
import com.aiblockclock.enums.execution.ExecutionStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "실행 진행 상황")
public record ExecutionProgressResponse(
        @Schema(description = "실행 ID", example = "8a1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String executionId,

        @Schema(description = "지금 상태")
        ExecutionStatus status,

        @Schema(description = "끝난 단계 수", example = "2")
        int done,

        @Schema(description = "돌아야 하는 단계 수", example = "3")
        int total,

        @Schema(description = "진행률", example = "66")
        int percent,

        @Schema(description = "지금 도는 단계 이름. 끝났으면 null", example = "가정통신문 작성", nullable = true)
        String currentStepName,

        @Schema(description = "단계별 상태")
        List<StepResponse> steps
) {
    public static ExecutionProgressResponse from(ExecutionProgress progress) {
        return new ExecutionProgressResponse(
                progress.id().value().toString(),
                progress.status(),
                progress.done(),
                progress.total(),
                progress.percent(),
                progress.currentStepName(),
                progress.steps().stream().map(StepResponse::from).toList());
    }

    @Schema(description = "단계 하나")
    public record StepResponse(
            @Schema(description = "단계 이름", example = "성적 분석")
            String name,

            @Schema(description = "단계 상태", example = "succeeded")
            String status
    ) {
        static StepResponse from(ExecutionProgress.Step step) {
            return new StepResponse(step.name(), step.status());
        }
    }
}
