package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AbcAmount(BigDecimal value) {

    private static final int DECIMAL_PLACES = 6;

    public AbcAmount {
        if (value == null || value.signum() <= 0) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        try {
            value = value.setScale(
                    DECIMAL_PLACES,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }
    }

    public static AbcAmount of(BigDecimal value) {
        return new AbcAmount(value);
    }
}
