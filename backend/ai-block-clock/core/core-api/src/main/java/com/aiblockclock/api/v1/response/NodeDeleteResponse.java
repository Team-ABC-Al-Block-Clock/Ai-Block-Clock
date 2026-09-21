package com.aiblockclock.api.v1.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "삭제 결과")
public record NodeDeleteResponse(
        @Schema(description = "삭제된 노드 ID", example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
        String nodeId,

        @Schema(description = "삭제 시각")
        Instant deletedAt
) {
    public static NodeDeleteResponse of(UUID nodeId, Instant deletedAt) {
        return new NodeDeleteResponse(nodeId.toString(), deletedAt);
    }
}
