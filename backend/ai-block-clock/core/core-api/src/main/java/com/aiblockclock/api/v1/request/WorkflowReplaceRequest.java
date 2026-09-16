package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "워크플로우 수정 요청. 보낸 구성이 저장된 구성을 통째로 대체한다")
public record WorkflowReplaceRequest(
        @Schema(description = "워크플로우 이름", example = "성적 분석 리포트 v2", maxLength = 40)
        @NotBlank @Size(max = 40) String name,

        @Schema(description = "수정 전에 읽은 저장 회차. 그 사이 다른 요청이 먼저 수정했으면 WORKFLOW_016 으로 거부한다",
                example = "1")
        @NotNull @Positive Long revision,

        @Schema(description = "캔버스에 놓인 노드. 최소 1개")
        @NotEmpty @Valid List<NodePlacementRequest> nodes,

        @Schema(description = "노드 연결선. 노드가 하나면 빈 배열")
        @NotNull @Valid List<EdgePlacementRequest> edges
) {
}
