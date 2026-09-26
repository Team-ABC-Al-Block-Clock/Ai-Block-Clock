package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Schema(description = "실행 요청")
public record ExecutionStartRequest(
        @Schema(description = "실행할 워크플로우 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        @NotNull UUID workflowId,

        @Schema(description = """
                실행 버튼을 누를 때 화면이 만드는 UUID. 같은 누름을 다시 보낼 때(재시도 · 연타)는 같은 값을 보낸다. \
                같은 값이 다시 오면 새로 차감하지 않고 앞서 접수한 실행을 돌려준다. 새 실행은 새 값으로 보낸다""",
                example = "5d3f9c1e-7b2a-4e8f-9a61-2c4b8d0e1f37")
        @NotNull UUID requestId,

        @Schema(description = """
                시작 노드의 입력 항목에 채운 값. `{ "<key>": 값 }` 모양의 JSON 문자열이다. \
                필수 항목이 빠졌거나, 타입 · 선택지가 맞지 않거나, 정의하지 않은 항목이 오면 `EXECUTION_007` 이다. \
                시작 노드가 없거나 입력 항목이 비어 있으면 보내지 않는다""",
                example = "{\"brand_name\":\"하루\",\"price_tier\":\"중가\"}", nullable = true)
        String input,

        @Schema(description = "사용자가 덧붙인 한 줄. 첫 단계에 함께 전달된다",
                example = "이번 달은 난이도가 높았습니다", maxLength = 500, nullable = true)
        @Size(max = 500) String comment,

        @Schema(description = "true 면 모델을 부르지 않고 파이프라인만 돈다. 비우면 false",
                example = "false", nullable = true)
        Boolean mock,

        @Schema(description = "첫 단계가 읽을 파일. 엑셀 또는 CSV, 10MB 까지", format = "binary", nullable = true)
        MultipartFile file
) {
    public boolean mockOrDefault() {
        return Boolean.TRUE.equals(mock);
    }

    public UploadedFile uploadedFile() {
        if (file == null) {
            return null;
        }

        try {
            return new UploadedFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new CoreException(ErrorType.FILE_NOT_STORED);
        }
    }
}
