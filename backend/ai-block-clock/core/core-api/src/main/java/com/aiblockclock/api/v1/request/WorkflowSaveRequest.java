package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "워크플로우 저장 요청")
public record WorkflowSaveRequest(
        @Schema(description = "워크플로우 이름", example = "성적 분석 리포트", maxLength = 40)
        @NotBlank @Size(max = 40) String name,

        @Schema(description = "캔버스에 놓인 노드. 최소 1개")
        @NotEmpty @Valid List<NodePlacementRequest> nodes,

        @Schema(description = "노드 연결선. 노드가 하나면 빈 배열")
        @NotNull @Valid List<EdgePlacementRequest> edges
) {
}
