package com.aiblockclock.domain.node;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RefFileValidator {
    private static final long MAX_TOTAL_SIZE = 10L * 1024 * 1024;

    public void validate(UploadedFile file, List<RefFile> attached) {
        if (file.size() == 0) {
            throw new CoreException(ErrorType.REF_FILE_EMPTY);
        }

        if (!RefFileFormat.isAccepted(file.filename())) {
            throw new CoreException(ErrorType.REF_FILE_TYPE_NOT_SUPPORTED,
                    Map.of("allowed", RefFileFormat.ACCEPTED_EXTENSIONS));
        }

        long total = attached.stream().mapToLong(RefFile::size).sum() + file.size();
        if (total > MAX_TOTAL_SIZE) {
            throw new CoreException(ErrorType.REF_FILE_TOTAL_TOO_LARGE,
                    Map.of("maxTotalSize", MAX_TOTAL_SIZE, "wouldBe", total));
        }
    }
}
