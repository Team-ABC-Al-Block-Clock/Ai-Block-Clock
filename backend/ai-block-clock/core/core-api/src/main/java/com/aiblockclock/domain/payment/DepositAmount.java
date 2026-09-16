package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record DepositAmount(BigDecimal value) {

    // 현재 입금 토큰인 USDC와 DB 금액 컬럼은 소수점 이하 6자리를 기준으로 한다.
    private static final int DECIMAL_PLACES = 6;

    public DepositAmount {
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

    public static DepositAmount of(BigDecimal value) {
        return new DepositAmount(value);
    }
}
