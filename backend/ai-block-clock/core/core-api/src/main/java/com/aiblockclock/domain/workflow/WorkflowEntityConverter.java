package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.domain.user.UserId;
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
