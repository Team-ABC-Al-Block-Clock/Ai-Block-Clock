package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionResultFormat;

public record ExecutionResult(ExecutionId executionId, ExecutionResultFormat format, String key, long size) {
}
