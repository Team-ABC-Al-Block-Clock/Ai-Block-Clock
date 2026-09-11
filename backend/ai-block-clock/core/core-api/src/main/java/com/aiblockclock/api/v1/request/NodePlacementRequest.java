package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.enums.workflow.SourceType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record NodePlacementRequest(
        @NotBlank @Size(max = 64) String nodeKey,
        @NotNull SourceType sourceType,
        @NotNull UUID sourceId
) {
    public static List<NodePlacement> toPlacements(List<NodePlacementRequest> nodes) {
        return nodes.stream()
                .map(node -> new NodePlacement(NodeKey.of(node.nodeKey()), node.sourceType(), node.sourceId()))
                .toList();
    }
}
