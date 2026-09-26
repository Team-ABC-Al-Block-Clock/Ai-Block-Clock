package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import org.springframework.stereotype.Component;

@Component
public final class AbcConversionPolicy {

    public AbcAmount convert(DepositAmount depositAmount) {
        if (depositAmount == null) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        return AbcAmount.of(depositAmount.value());
    }
}
