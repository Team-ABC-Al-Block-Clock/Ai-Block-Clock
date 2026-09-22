package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.EdgeKey;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.NodeKey;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "노드 연결선. 실재하는 nodeKey 끼리만 이을 수 있다")
public record EdgePlacementRequest(
        @Schema(description = "이 워크플로우 안에서 연결선을 가리키는 키", example = "e1", maxLength = 64)
        @NotBlank @Size(max = 64) String edgeKey,

        @Schema(description = "시작 노드의 nodeKey", example = "n1", maxLength = 64)
        @NotBlank @Size(max = 64) String sourceNodeKey,

        @Schema(description = "도착 노드의 nodeKey", example = "n2", maxLength = 64)
        @NotBlank @Size(max = 64) String targetNodeKey
) {
    public static List<GraphEdge> toEdges(List<EdgePlacementRequest> edges) {
        return edges.stream()
                .map(edge -> new GraphEdge(
                        EdgeKey.of(edge.edgeKey()),
                        NodeKey.of(edge.sourceNodeKey()),
                        NodeKey.of(edge.targetNodeKey())))
                .toList();
    }
}
