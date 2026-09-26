package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.storage.execution.ExecutionResultRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExecutionResultReader {
    private final ExecutionResultRepository executionResultRepository;

    @Transactional(readOnly = true)
    public Optional<ExecutionResult> read(ExecutionId executionId, ExecutionResultFormat format) {
        return executionResultRepository.findByExecutionIdAndFormat(executionId.value(), format)
                .map(ExecutionResultEntityConverter::toDomain);
    }

    @Transactional(readOnly = true)
    public List<ExecutionResultFormat> readFormats(ExecutionId executionId) {
        return executionResultRepository.findByExecutionIdOrderByIdAsc(executionId.value()).stream()
                .map(entity -> entity.getFormat())
                .toList();
    }
}
