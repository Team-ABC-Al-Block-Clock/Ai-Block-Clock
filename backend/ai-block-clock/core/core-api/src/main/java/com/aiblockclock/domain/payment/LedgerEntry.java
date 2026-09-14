package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;

import java.math.BigDecimal;

public record LedgerEntry(
        LedgerAccountType accountType,
        String accountReferenceId,
        BigDecimal amountDelta
) {
}
