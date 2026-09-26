package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ExecutionFileValidator {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".xlsx", ".xls", ".csv");
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    public void validate(UploadedFile file) {
        if (file.size() == 0) {
            throw new CoreException(ErrorType.EXECUTION_FILE_EMPTY);
        }

        if (file.size() > MAX_SIZE) {
            throw new CoreException(ErrorType.EXECUTION_FILE_TOO_LARGE, Map.of("maxSize", MAX_SIZE));
        }

        if (!ALLOWED_EXTENSIONS.contains(file.extension())) {
            throw new CoreException(ErrorType.EXECUTION_FILE_TYPE_NOT_SUPPORTED,
                    Map.of("allowed", ALLOWED_EXTENSIONS));
        }
    }
}
