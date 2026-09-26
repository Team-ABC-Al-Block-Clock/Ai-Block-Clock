package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.execution.ExecutionResultEntity;

public final class ExecutionResultEntityConverter {
    private ExecutionResultEntityConverter() {
    }

    public static ExecutionResultEntity toEntity(ExecutionResult result) {
        return new ExecutionResultEntity(
                result.executionId().value(), result.format(), result.key(), result.size());
    }

    public static ExecutionResult toDomain(ExecutionResultEntity entity) {
        return new ExecutionResult(ExecutionId.of(entity.getExecutionId()), entity.getFormat(),
                entity.getFileKey(), entity.getFileSize());
    }
}
