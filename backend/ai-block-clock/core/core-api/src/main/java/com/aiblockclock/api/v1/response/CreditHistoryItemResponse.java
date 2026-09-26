package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.ChargeRequest;
import com.aiblockclock.domain.payment.CreditHistoryItem;
import com.aiblockclock.domain.payment.PayoutTransaction;
import com.aiblockclock.domain.payment.Withdrawal;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.enums.payment.CreditHistoryType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 충전과 출금이 같은 칸을 쓴다. 한쪽에만 있는 값(실패 사유 · 반려 사유)은 다른 쪽에서 null 이다
@Schema(description = "충전 · 출금 내역 한 줄")
public record CreditHistoryItemResponse(
        @Schema(description = "CHARGE(충전) · WITHDRAWAL(출금)", example = "WITHDRAWAL")
        CreditHistoryType type,

        @Schema(description = "충전 요청 번호 또는 출금 신청 번호. type 마다 따로 매긴다", example = "42")
        String id,

        @Schema(description = """
                type 마다 다르다.
                CHARGE: PENDING(확인 중) · CONFIRMED(적립 완료) · FAILED(실패).
                WITHDRAWAL: REQUESTED(심사 대기) · CANCELED(취소) · APPROVED(승인, 지급 대기) · REJECTED(반려) ·
                PAYING(체인에 보냄, 확인 중) · ON_HOLD(결과 확인이 늦어짐, 금액은 묶여 있음) · PAID(지급 완료) ·
                FAILED(체인이 되돌림, 금액은 정산금으로 돌아옴)""", example = "PAYING")
        String status,

        @Schema(description = "ABC. 충전은 적립됐으면 적립액, 아니면 요청액이다", example = "10.000000")
        String amount,

        @Schema(description = "지갑 주소. 충전은 보낸 지갑, 출금은 받을 지갑",
                example = "0x1111111111111111111111111111111111111111")
        String walletAddress,

        @Schema(description = "체인 거래 해시. 출금은 체인에 보낸 뒤(PAYING 부터)에만 있다", nullable = true)
        String transactionHash,

        @Schema(description = "요청 · 신청을 받은 시각. 목록은 이 시각의 최신순이다")
        Instant requestedAt,

        @Schema(description = "끝난 시각. 충전은 적립한 시각, 출금은 지급을 마친 시각. 끝나기 전이면 없다", nullable = true)
        Instant completedAt,

        @Schema(description = "충전이 실패했을 때만 온다. 값은 충전 상태 조회의 failureReason 과 같다", nullable = true)
        ChargeStatusReason failureReason,

        @Schema(description = "출금이 반려됐을 때만 온다", nullable = true)
        String rejectReason
) {
    public static CreditHistoryItemResponse from(CreditHistoryItem item) {
        return switch (item) {
            case CreditHistoryItem.Charge charge -> fromCharge(charge.request());
            case CreditHistoryItem.Payout payout -> fromWithdrawal(payout.withdrawal());
        };
    }

    private static CreditHistoryItemResponse fromCharge(ChargeRequest charge) {
        AbcAmount credited = charge.getCreditedAbcAmount();

        return new CreditHistoryItemResponse(
                CreditHistoryType.CHARGE,
                Long.toString(charge.getId()),
                charge.getStatus().name(),
                credited == null
                        ? charge.getRequestedDepositAmount().value().toPlainString()
                        : credited.value().toPlainString(),
                charge.getSenderWalletAddress(),
                charge.getTransactionHash(),
                charge.getRequestedAt(),
                charge.getConfirmedAt(),
                charge.getStatusReason(),
                null
        );
    }

    private static CreditHistoryItemResponse fromWithdrawal(Withdrawal withdrawal) {
        PayoutTransaction transaction = withdrawal.getPayoutTransaction();

        return new CreditHistoryItemResponse(
                CreditHistoryType.WITHDRAWAL,
                Long.toString(withdrawal.getId()),
                withdrawal.getStatus().name(),
                withdrawal.getAmount().value().toPlainString(),
                withdrawal.getWalletAddress(),
                transaction == null ? null : transaction.transactionHash(),
                withdrawal.getRequestedAt(),
                withdrawal.getPaidAt(),
                null,
                withdrawal.getRejectReason()
        );
    }
}
