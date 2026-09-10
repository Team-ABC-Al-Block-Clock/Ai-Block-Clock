package com.aiblockclock.domain.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AbcConversionPolicy {

    public BigDecimal convert(BigDecimal usdcAmount) {
        if (usdcAmount == null
                || usdcAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "USDC 수량은 0보다 커야 합니다."
            );
        }

        return usdcAmount.setScale(
                3,
                RoundingMode.UNNECESSARY
        );
    }
}
