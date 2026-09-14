package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;

import java.util.List;

public record LedgerTransaction(
        LedgerEventType eventType,
        String idempotencyKey,
        String referenceType,
        String referenceId,
        Long chainId,
        String transactionHash,
        List<LedgerEntry> entries
) {

    public LedgerTransaction {
        entries = List.copyOf(entries);
    }
}
