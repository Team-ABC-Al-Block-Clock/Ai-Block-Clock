package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.EdgeKey;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.NodeKey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EdgePlacementRequest(
        @NotBlank @Size(max = 64) String edgeKey,
        @NotBlank @Size(max = 64) String sourceNodeKey,
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
