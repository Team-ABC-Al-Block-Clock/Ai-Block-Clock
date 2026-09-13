package com.aiblockclock.api.payment.response;

import com.aiblockclock.domain.payment.CreditBalance;

import java.math.BigDecimal;

public record CreditBalanceResponse(BigDecimal balance) {

    public static CreditBalanceResponse from(
            CreditBalance creditBalance
    ) {
        return new CreditBalanceResponse(
                creditBalance.amount()
        );
    }
}
