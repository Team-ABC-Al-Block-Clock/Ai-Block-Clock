package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.SourceType;

import java.time.Instant;
import java.util.List;

public record WorkflowDetailResponse(
        String workflowId,
        String name,
        long revision,
        List<NodeResponse> nodes,
        List<EdgeResponse> edges,
        String estimatedCost,
        String currency,
        Instant lastExecutedAt,
        Instant updatedAt
) {
    public static WorkflowDetailResponse from(Workflow workflow) {
        List<NodeResponse> nodes = workflow.getGraph().getNodes().stream()
                .map(NodeResponse::from)
                .toList();
        List<EdgeResponse> edges = workflow.getGraph().getEdges().stream()
                .map(edge -> new EdgeResponse(
                        edge.key().value(),
                        edge.sourceNodeKey().value(),
                        edge.targetNodeKey().value()))
                .toList();

        return new WorkflowDetailResponse(
                workflow.getId().value().toString(),
                workflow.getName(),
                workflow.getRevision().value(),
                nodes,
                edges,
                WorkflowPricing.UNPRICED,
                WorkflowPricing.CURRENCY,
                workflow.getTimestamps().lastExecutedAt(),
                workflow.getTimestamps().updatedAt());
    }

    public record NodeResponse(String nodeKey, SourceType sourceType, String sourceId, String versionId) {
        static NodeResponse from(GraphNode node) {
            return new NodeResponse(
                    node.key().value(),
                    node.sourceType(),
                    node.reference().referenceId().toString(),
                    node.reference().hasVersion() ? node.reference().versionId().toString() : null);
        }
    }

    public record EdgeResponse(String edgeKey, String sourceNodeKey, String targetNodeKey) {
    }
}
