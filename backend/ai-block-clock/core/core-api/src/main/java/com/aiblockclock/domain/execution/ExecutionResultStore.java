package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.FileStore;
import com.aiblockclock.enums.execution.ExecutionResultFormat;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ExecutionResultStore {
    private final FileStore fileStore;

    public ExecutionResult write(ExecutionId executionId, ExecutionResultFormat format, byte[] content) {
        ExecutionResult result = new ExecutionResult(executionId, format,
                "executions/" + executionId.value() + "/" + format.filename(), content.length);

        fileStore.write(result.key(), content);

        return result;
    }

    public Path locate(ExecutionResult result) {
        return fileStore.locate(result.key());
    }

    public void erase(ExecutionResult result) {
        fileStore.erase(result == null ? null : result.key());
    }
}
