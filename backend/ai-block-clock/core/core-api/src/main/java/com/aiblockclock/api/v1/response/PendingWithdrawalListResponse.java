package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.PendingWithdrawal;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "심사를 기다리는 출금 신청. 오래된 신청부터다")
public record PendingWithdrawalListResponse(
        @Schema(description = "심사 대기 출금 신청")
        List<PendingWithdrawalResponse> items
) {
    public static PendingWithdrawalListResponse from(List<PendingWithdrawal> pendings) {
        return new PendingWithdrawalListResponse(pendings.stream()
                .map(PendingWithdrawalResponse::from)
                .toList());
    }

    @Schema(description = "심사할 출금 신청 하나와 신청한 사람의 본인 확인")
    public record PendingWithdrawalResponse(
            @Schema(description = "출금 신청")
            WithdrawalResponse withdrawal,

            @Schema(description = "신청한 사용자 ID", example = "12")
            String requesterId,

            @Schema(description = "신청한 사람의 본인 확인. 본인 확인 검사가 생기기 전의 신청이면 없다", nullable = true)
            IdentityVerificationResponse identity
    ) {
        static PendingWithdrawalResponse from(PendingWithdrawal pending) {
            return new PendingWithdrawalResponse(
                    WithdrawalResponse.from(pending.withdrawal()),
                    Long.toString(pending.withdrawal().getRequester().value()),
                    pending.identity() == null ? null : IdentityVerificationResponse.from(pending.identity()));
        }
    }
}
