package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.execution.ExecutionResultRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ExecutionResultWriter {
    private final ExecutionResultRepository executionResultRepository;

    @Transactional
    public void append(ExecutionResult result) {
        executionResultRepository.save(ExecutionResultEntityConverter.toEntity(result));
    }
}
