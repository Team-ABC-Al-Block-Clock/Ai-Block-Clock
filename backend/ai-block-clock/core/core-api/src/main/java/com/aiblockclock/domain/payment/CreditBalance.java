package com.aiblockclock.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

public record CreditBalance(BigDecimal amount) {

    private static final BigDecimal ZERO_AMOUNT =
            BigDecimal.ZERO.setScale(3);

    public CreditBalance {
        Objects.requireNonNull(amount);
    }

    public static CreditBalance from(BigDecimal amount) {
        return new CreditBalance(amount);
    }

    public static CreditBalance zero() {
        return new CreditBalance(ZERO_AMOUNT);
    }
}
