package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.Timestamps;
<<<<<<< HEAD
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.workflow.WorkflowEdgeEntity;
=======
>>>>>>> bb207d1 ([S15P21A403-155] refactor: 그래프 저장을 워크플로우·에이전트 공용으로 뺀다)
import com.aiblockclock.storage.workflow.WorkflowEntity;

final class WorkflowEntityConverter {
    private WorkflowEntityConverter() {
    }

    static Workflow toDomain(WorkflowEntity entity, WorkflowGraph graph) {
        return Workflow.restore(
                WorkflowId.of(entity.getId()),
                UserId.of(entity.getOwnerId()),
                entity.getName(),
                entity.getDescription(),
                graph,
                entity.getValidationStatus(),
                Revision.of(entity.getRevision()),
                new Timestamps(entity.getCreatedAt(), entity.getUpdatedAt()),
                entity.getLastExecutedAt());
    }

<<<<<<< HEAD
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
                UserId.of(entity.getOwnerId()),
                entity.getName(),
                entity.getDescription(),
                WorkflowGraph.of(nodes, edges),
                entity.getValidationStatus(),
                Revision.of(entity.getRevision()),
                new Timestamps(entity.getCreatedAt(), entity.getUpdatedAt()),
                entity.getLastExecutedAt());
    }

=======
>>>>>>> bb207d1 ([S15P21A403-155] refactor: 그래프 저장을 워크플로우·에이전트 공용으로 뺀다)
    static WorkflowSummary toSummary(WorkflowEntity entity) {
        return new WorkflowSummary(
                WorkflowId.of(entity.getId()),
                entity.getName(),
                entity.getLastExecutedAt(),
                entity.getUpdatedAt());
    }

    static WorkflowEntity toEntity(Workflow workflow, GraphId graphId) {
        return new WorkflowEntity(
                workflow.getId().value(),
                workflow.getOwner().value(),
                graphId.value(),
                workflow.getName(),
                workflow.getDescription(),
                workflow.getValidationStatus(),
                workflow.getRevision().value());
    }
}
