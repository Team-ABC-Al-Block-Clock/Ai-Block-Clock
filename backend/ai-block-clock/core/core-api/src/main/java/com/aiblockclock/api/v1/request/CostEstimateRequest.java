package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "실행 견적 요청")
public record CostEstimateRequest(
        @Schema(description = "견적을 볼 워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        @NotNull UUID workflowId
) {
}
