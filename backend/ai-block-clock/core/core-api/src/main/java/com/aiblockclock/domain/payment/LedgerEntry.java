package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record LedgerEntry(
        LedgerAccountType accountType,
        String accountReferenceId,
        BigDecimal amountDelta
) {

    private static final int ABC_DECIMAL_PLACES = 6;

    public LedgerEntry {
        if (accountType == null
                || accountReferenceId == null
                || accountReferenceId.isBlank()
                || amountDelta == null
                || amountDelta.signum() == 0) {
            throw new CoreException(
                    ErrorType.LEDGER_INVALID_TRANSACTION
            );
        }

        try {
            amountDelta = amountDelta.setScale(
                    ABC_DECIMAL_PLACES,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
        }
    }
}
