package com.aiblockclock.api.v1.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "삭제 결과")
public record WorkflowDeleteResponse(
        @Schema(description = "삭제된 워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String workflowId,

        @Schema(description = "삭제 시각")
        Instant deletedAt
) {
    public static WorkflowDeleteResponse of(UUID workflowId, Instant deletedAt) {
        return new WorkflowDeleteResponse(workflowId.toString(), deletedAt);
    }
}
