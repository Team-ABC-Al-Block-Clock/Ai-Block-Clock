package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.storage.workflow.WorkflowEdgeEntity;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WorkflowEntityConverter {
    private WorkflowEntityConverter() {
    }

    static Workflow toDomain(WorkflowEntity entity,
                             List<WorkflowNodeEntity> nodeRows,
                             List<WorkflowEdgeEntity> edgeRows) {
        List<GraphNode> nodes = nodeRows.stream()
                .map(WorkflowEntityConverter::toDomain)
                .toList();
        List<GraphEdge> edges = edgeRows.stream()
                .map(WorkflowEntityConverter::toDomain)
                .toList();

        return Workflow.restore(
                WorkflowId.of(entity.getId()),
                OwnerId.of(entity.getOwnerId()),
                entity.getName(),
                entity.getDescription(),
                WorkflowGraph.of(nodes, edges),
                entity.getValidationStatus(),
                Revision.of(entity.getRevision()),
                new WorkflowTimestamps(
                        entity.getCreatedAt(),
                        entity.getUpdatedAt(),
                        entity.getLastExecutedAt()));
    }

    static WorkflowSummary toSummary(WorkflowEntity entity) {
        return new WorkflowSummary(
                WorkflowId.of(entity.getId()),
                entity.getName(),
                entity.getLastExecutedAt(),
                entity.getUpdatedAt());
    }

    static WorkflowEntity toEntity(Workflow workflow) {
        return new WorkflowEntity(
                workflow.getId().value(),
                workflow.getOwner().value(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getValidationStatus(),
                workflow.getRevision().value());
    }

    static List<WorkflowNodeEntity> toNodeEntities(UUID workflowId, WorkflowGraph graph) {
        return graph.getNodes().stream()
                .map(node -> new WorkflowNodeEntity(
                        workflowId,
                        node.key().value(),
                        node.sourceType(),
                        node.reference().referenceId(),
                        node.reference().versionId(),
                        node.inputSource()))
                .toList();
    }

    static List<WorkflowEdgeEntity> toEdgeEntities(UUID workflowId, WorkflowGraph graph) {
        return graph.getEdges().stream()
                .map(edge -> new WorkflowEdgeEntity(
                        workflowId,
                        edge.key().value(),
                        edge.sourceNodeKey().value(),
                        edge.targetNodeKey().value()))
                .toList();
    }

    private static GraphNode toDomain(WorkflowNodeEntity node) {
        NodeKey key = NodeKey.of(node.getNodeKey());
        return switch (node.getSourceType()) {
            case NODE -> {
                if (node.getVersionId() != null) {
                    throw new CoreException(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH,
                            Map.of("nodeKey", key.value(), "sourceType", node.getSourceType().name()));
                }

                yield new BaseNode(key, node.getReferenceId(), node.getInputSource());
            }
            case AGENT -> new AgentNode(key, node.getReferenceId(), node.getVersionId(),
                    node.getInputSource());
        };
    }

    private static GraphEdge toDomain(WorkflowEdgeEntity edge) {
        return new GraphEdge(
                EdgeKey.of(edge.getEdgeKey()),
                NodeKey.of(edge.getSourceNodeKey()),
                NodeKey.of(edge.getTargetNodeKey()));
    }
}
