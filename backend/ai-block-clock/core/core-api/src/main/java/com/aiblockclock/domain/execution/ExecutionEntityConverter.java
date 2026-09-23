package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.storage.execution.ExecutionEntity;

public final class ExecutionEntityConverter {
    private ExecutionEntityConverter() {
    }

    public static ExecutionEntity toEntity(Execution execution) {
        return new ExecutionEntity(
                execution.getId().value(),
                execution.getWorkflowId().value(),
                execution.getRequester().value(),
                execution.getRunId().value(),
                execution.getStatus(),
                execution.getPromptCount());
    }

    public static Execution toDomain(ExecutionEntity entity) {
        return Execution.restore(
                ExecutionId.of(entity.getId()),
                WorkflowId.of(entity.getWorkflowId()),
                UserId.of(entity.getRequesterId()),
                RunId.of(entity.getRunId()),
                entity.getStatus(),
                entity.getPromptCount());
    }
}
