package com.aiblockclock.api.payment.response;

import com.aiblockclock.domain.payment.AbcBalance;

import java.math.BigDecimal;

public record AbcBalanceResponse(BigDecimal balance) {

    public static AbcBalanceResponse from(
            AbcBalance abcBalance
    ) {
        return new AbcBalanceResponse(
                abcBalance.amount()
        );
    }
}
