package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExecutionWriter {
    private final ExecutionRepository executionRepository;

    @Transactional
    public Execution append(Execution execution) {
        return ExecutionEntityConverter.toDomain(
                executionRepository.save(ExecutionEntityConverter.toEntity(execution)));
    }

    @Transactional
    public void moveTo(ExecutionId id, ExecutionStatus status) {
        ExecutionEntity entity = executionRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));

        entity.moveTo(status);
    }
}
