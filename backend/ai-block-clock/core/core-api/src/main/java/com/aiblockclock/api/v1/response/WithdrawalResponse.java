package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.Withdrawal;
import com.aiblockclock.enums.payment.WithdrawalStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 신청 · 취소 · 관리자 심사가 같은 모양을 돌려준다. FE 는 응답을 같은 코드로 그린다
@Schema(description = "출금 신청 한 건")
public record WithdrawalResponse(
        @Schema(description = "출금 신청 번호. 취소 · 심사할 때 쓴다", example = "42")
        String withdrawalId,

        @Schema(description = "REQUESTED(심사 대기) · CANCELED(취소) · APPROVED(승인, 지급 대기) · REJECTED(반려)",
                example = "REQUESTED")
        WithdrawalStatus status,

        @Schema(description = "출금할 ABC", example = "10.000000")
        String amount,

        @Schema(description = "받을 지갑. 신청할 때의 로그인 지갑이다",
                example = "0x1111111111111111111111111111111111111111")
        String walletAddress,

        @Schema(description = "신청을 받은 시각")
        Instant requestedAt,

        @Schema(description = "관리자가 심사한 시각. 심사 전이면 없다", nullable = true)
        Instant reviewedAt,

        @Schema(description = "반려 사유. 반려됐을 때만 있다", nullable = true)
        String rejectReason
) {
    public static WithdrawalResponse from(Withdrawal withdrawal) {
        return new WithdrawalResponse(
                Long.toString(withdrawal.getId()),
                withdrawal.getStatus(),
                withdrawal.getAmount().value().toPlainString(),
                withdrawal.getWalletAddress(),
                withdrawal.getRequestedAt(),
                withdrawal.getReviewedAt(),
                withdrawal.getRejectReason()
        );
    }
}
