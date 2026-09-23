package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "실행 요청")
public record ExecutionStartRequest(
        @Schema(description = "실행할 워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        @NotNull UUID workflowId,

        @Schema(description = "사용자가 덧붙인 한 줄. 첫 단계에 함께 전달된다",
                example = "이번 달은 난이도가 높았습니다", maxLength = 500, nullable = true)
        @Size(max = 500) String comment,

        @Schema(description = "true 면 모델을 부르지 않고 파이프라인만 돈다. 비우면 false",
                example = "false", nullable = true)
        Boolean mock
) {
    public boolean mockOrDefault() {
        return Boolean.TRUE.equals(mock);
    }
}
