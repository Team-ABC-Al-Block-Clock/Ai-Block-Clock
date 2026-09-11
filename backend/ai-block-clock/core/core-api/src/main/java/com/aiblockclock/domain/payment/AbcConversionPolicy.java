package com.aiblockclock.domain.payment;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public final class AbcConversionPolicy {

    public BigDecimal convert(BigDecimal usdcAmount) {
        if (usdcAmount == null
                || usdcAmount.signum() <= 0) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        try {
            return usdcAmount.setScale(
                    3,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }
    }
}
