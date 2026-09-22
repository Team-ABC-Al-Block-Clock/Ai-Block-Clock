package com.aiblockclock.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

public record AbcBalance(BigDecimal amount) {

    private static final BigDecimal ZERO_AMOUNT =
            BigDecimal.ZERO.setScale(3);

    public AbcBalance {
        Objects.requireNonNull(amount);
    }

    public static AbcBalance from(BigDecimal amount) {
        return new AbcBalance(amount);
    }

    public static AbcBalance zero() {
        return new AbcBalance(ZERO_AMOUNT);
    }
}
