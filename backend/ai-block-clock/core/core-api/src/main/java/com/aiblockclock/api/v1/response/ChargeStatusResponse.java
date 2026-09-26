package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.ChargeRequest;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// 충전 요청과 상태 조회가 같은 모양을 돌려준다. FE 는 요청 직후 받은 것과 다시 물어 받은 것을 같은 코드로 그린다
@Schema(description = "충전 한 건의 확인 상태")
public record ChargeStatusResponse(
        @Schema(description = "충전 요청 번호. 상태를 다시 물을 때 쓴다", example = "42")
        String chargeId,

        @Schema(description = "PENDING(확인 중) · CONFIRMED(적립 완료) · FAILED(실패)", example = "PENDING")
        ChargeStatus status,

        @Schema(description = """
                실패했을 때만 온다.
                ONCHAIN_TRANSACTION_FAILED(거래가 되돌려짐) · VERIFICATION_TIMEOUT(30분 안에 확인되지 않음) ·
                SENDER_WALLET_MISMATCH(로그인 지갑이 보낸 거래가 아님) · CUSTODY_ADDRESS_MISMATCH(보관 지갑으로 가지 않음) ·
                DEPOSIT_TOKEN_CONTRACT_MISMATCH(abcToken 이 아님) · AMOUNT_MISMATCH(요청 금액과 보낸 금액이 다름) ·
                CHAIN_ID_MISMATCH(다른 체인의 거래)""", nullable = true)
        ChargeStatusReason failureReason,

        @Schema(description = "거래 해시 (소문자)")
        String transactionHash,

        @Schema(description = "요청한 ABC", example = "10.000000")
        String requestedAmount,

        @Schema(description = "적립된 ABC. 완료됐을 때만 온다", example = "10.000000", nullable = true)
        String creditedAmount,

        @Schema(description = "요청을 받은 시각")
        Instant requestedAt,

        @Schema(description = "적립한 시각. 완료됐을 때만 온다", nullable = true)
        Instant confirmedAt
) {
    public static ChargeStatusResponse from(ChargeRequest chargeRequest) {
        AbcAmount credited = chargeRequest.getCreditedAbcAmount();

        return new ChargeStatusResponse(
                Long.toString(chargeRequest.getId()),
                chargeRequest.getStatus(),
                chargeRequest.getStatusReason(),
                chargeRequest.getTransactionHash(),
                chargeRequest.getRequestedDepositAmount().value().toPlainString(),
                credited == null ? null : credited.value().toPlainString(),
                chargeRequest.getRequestedAt(),
                chargeRequest.getConfirmedAt()
        );
    }
}
