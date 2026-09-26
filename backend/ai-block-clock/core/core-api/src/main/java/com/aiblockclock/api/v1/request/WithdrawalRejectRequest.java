package com.aiblockclock.api.v1.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "출금 신청 반려")
public record WithdrawalRejectRequest(
        // 돈이 나가지 않은 까닭을 공급자가 알아야 해서 비울 수 없다
        @Schema(description = "반려 사유. 공급자에게 그대로 보인다", example = "정산 내역 확인이 필요합니다")
        @NotBlank
        @Size(max = 200)
        String reason
) {
}
