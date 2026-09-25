package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.execution.ExecutionResultRepository;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ExecutionResultWriter {
    private static final String FORMAT_UNIQUE_CONSTRAINT = "uk_execution_result_format";

    private final ExecutionResultRepository executionResultRepository;

    public void append(ExecutionResult result) {
        try {
            executionResultRepository.saveAndFlush(ExecutionResultEntityConverter.toEntity(result));
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateFormat(exception)) {
                throw exception;
            }
        }
    }

    private boolean isDuplicateFormat(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                return constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(FORMAT_UNIQUE_CONSTRAINT);
            }
        }

        return false;
    }
}
