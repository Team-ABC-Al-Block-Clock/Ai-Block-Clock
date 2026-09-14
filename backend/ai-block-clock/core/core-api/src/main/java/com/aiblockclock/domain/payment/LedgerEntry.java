package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.math.BigDecimal;

public record LedgerEntry(
        LedgerAccountType accountType,
        String accountReferenceId,
        BigDecimal amountDelta
) {

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
    }
}
