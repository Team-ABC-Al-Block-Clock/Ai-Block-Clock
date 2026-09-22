package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.payment.AbcBalance;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "로그인 사용자의 ABC 잔액")
public record AbcBalanceResponse(
        @Schema(description = "지금 쓸 수 있는 ABC", example = "70.000000")
        String availableAmount,

        @Schema(description = "실행 시작 때 선차감되어 결과를 기다리는 ABC", example = "30.000000")
        String reservedAmount
) {
    public static AbcBalanceResponse from(AbcBalance balance) {
        return new AbcBalanceResponse(
                balance.available().toPlainString(),
                balance.reserved().toPlainString()
        );
    }
}
