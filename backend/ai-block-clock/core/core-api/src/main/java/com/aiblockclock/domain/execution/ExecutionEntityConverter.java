package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
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
                execution.getRequestId().value(),
                execution.getRequester().value(),
                execution.isDispatched() ? execution.getRunId().value() : null,
                execution.getStatus(),
                execution.getPromptCount(),
                keyOf(execution.getInputFile()),
                nameOf(execution.getInputFile()),
                sizeOf(execution.getInputFile()),
                execution.isMock(),
                execution.isReserved() ? execution.getReserved().value() : null);
    }

    public static Execution toDomain(ExecutionEntity entity) {
        return Execution.restore(
                ExecutionId.of(entity.getId()),
                ExecutionRequestId.of(entity.getRequestId()),
                WorkflowId.of(entity.getWorkflowId()),
                UserId.of(entity.getRequesterId()),
                entity.getRunId() == null ? null : RunId.of(entity.getRunId()),
                entity.getStatus(),
                entity.getPromptCount(),
                inputFileOf(entity),
                entity.isMock(),
                entity.getReservedAbc() == null ? null : AbcAmount.of(entity.getReservedAbc()));
    }

    private static ExecutionFile inputFileOf(ExecutionEntity entity) {
        if (entity.getInputFileKey() == null) {
            return null;
        }

        return new ExecutionFile(
                entity.getInputFileKey(), entity.getInputFileName(), entity.getInputFileSize());
    }

    private static String keyOf(ExecutionFile file) {
        return file == null ? null : file.key();
    }

    private static String nameOf(ExecutionFile file) {
        return file == null ? null : file.filename();
    }

    private static Long sizeOf(ExecutionFile file) {
        return file == null ? null : file.size();
    }
}
