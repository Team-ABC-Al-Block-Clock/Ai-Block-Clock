package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.AbcTransaction;
import com.aiblockclock.enums.payment.LedgerEventType;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "ABC 사용 내역 한 줄")
public record AbcTransactionResponse(
        @Schema(description = "장부 거래 ID", example = "41")
        Long transactionId,

        @Schema(description = "CHARGE 충전 · RESERVE 실행 선차감 · REFUND 환불 · PAYOUT_CONVERSION 정산금에서 옮겨 옴")
        LedgerEventType transactionType,

        @Schema(description = "부호 포함 변동액. 들어오면 +, 나가면 -", example = "-30.000000")
        String amount,

        @Schema(description = "발생 시각")
        Instant occurredAt,

        @Schema(description = "실행 때문에 생긴 줄이면 그 실행 ID, 아니면 null", nullable = true)
        String executionId,

        @Schema(description = "온체인 거래가 있는 줄(충전)만 값이 있다", nullable = true)
        String transactionHash
) {
    // 장부가 "실행" 을 근거로 남길 때 쓰는 referenceType 값이다.
    private static final String EXECUTION_REFERENCE_TYPE = "EXECUTION";

    public static AbcTransactionResponse from(AbcTransaction transaction) {
        return new AbcTransactionResponse(
                transaction.transactionId(),
                transaction.eventType(),
                transaction.amount().toPlainString(),
                transaction.occurredAt(),
                executionIdOf(transaction),
                transaction.transactionHash()
        );
    }

    private static String executionIdOf(AbcTransaction transaction) {
        if (!EXECUTION_REFERENCE_TYPE.equals(transaction.referenceType())) {
            return null;
        }

        return transaction.referenceId();
    }
}
