package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "본인 확인 반려")
public record IdentityVerificationRejectRequest(
        // 사용자가 무엇을 고쳐 다시 내야 하는지 알아야 해서 비울 수 없다
        @Schema(description = "반려 사유. 사용자에게 그대로 보인다", example = "실명이 지갑 등록 정보와 다릅니다")
        @NotBlank
        @Size(max = 200)
        String reason
) {
}
