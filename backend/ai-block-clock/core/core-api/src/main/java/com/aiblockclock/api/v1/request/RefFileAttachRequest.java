package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Schema(description = "노드에 붙일 참고 자료")
public record RefFileAttachRequest(
        @Schema(description = "PDF · 텍스트 · 마크다운 · CSV. 노드당 합계 10MB",
                format = "binary")
        @NotNull MultipartFile file
) {
    public UploadedFile uploadedFile() {
        try {
            return new UploadedFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new CoreException(ErrorType.FILE_NOT_STORED);
        }
    }
}
