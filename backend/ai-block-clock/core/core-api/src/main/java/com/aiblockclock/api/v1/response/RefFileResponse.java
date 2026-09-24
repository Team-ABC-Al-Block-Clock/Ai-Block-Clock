package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.node.RefFile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "노드에 붙인 참고 자료")
public record RefFileResponse(
        @Schema(description = "자료 ID", example = "7c2f9a10-3e55-4c8b-9a1d-8f3e2b5c7d90")
        String refFileId,

        @Schema(description = "올린 파일 이름", example = "채점 기준표.pdf")
        String filename,

        @Schema(description = "바이트", example = "204800")
        long size
) {
    public static RefFileResponse from(RefFile file) {
        return new RefFileResponse(file.id().value().toString(), file.filename(), file.size());
    }

    public static List<RefFileResponse> from(List<RefFile> files) {
        return files.stream().map(RefFileResponse::from).toList();
    }
}
