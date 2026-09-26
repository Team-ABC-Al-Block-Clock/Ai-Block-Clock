package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.file.FileStore;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ExecutionFileStore {
    private static final String STORED_NAME = "input";

    private final FileStore fileStore;

    public ExecutionFile write(ExecutionId executionId, UploadedFile upload) {
        ExecutionFile file = new ExecutionFile(
                "executions/" + executionId.value() + "/" + STORED_NAME + upload.extension(),
                upload.filename(),
                upload.size());

        fileStore.write(file.key(), upload.content());

        return file;
    }

    public Path locate(ExecutionFile file) {
        return fileStore.locate(file.key());
    }

    public void erase(ExecutionFile file) {
        fileStore.erase(file == null ? null : file.key());
    }
}
