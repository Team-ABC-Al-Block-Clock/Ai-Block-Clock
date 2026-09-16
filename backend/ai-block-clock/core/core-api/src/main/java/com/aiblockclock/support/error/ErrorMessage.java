package com.aiblockclock.support.error;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;

@Getter
@Schema(description = "실패 응답의 오류 본문")
public class ErrorMessage {
    @Schema(description = "클라이언트가 분기 기준으로 삼는 코드", example = "WORKFLOW_002")
    private final String code;

    @Schema(description = "사람이 읽는 설명", example = "워크플로우를 찾을 수 없습니다.")
    private final String message;

    @Schema(description = "오류를 짚어 주는 값. 응답마다 모양이 다르고, 없으면 null",
            example = "{\"limit\": 10, \"actual\": 12}")
    private final Object data;

    public ErrorMessage(ErrorType errorType, Object data) {
        this.code = errorType.getCode().name();
        this.message = errorType.getMessage();
        this.data = data;
    }
}
