package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;

import java.math.BigDecimal;
import java.time.Instant;

public record AbcTransaction(
        Long transactionId,
        LedgerEventType eventType,
        BigDecimal amount,
        Instant occurredAt
) {

    public static AbcTransaction of(
            Long transactionId,
            LedgerEventType eventType,
            BigDecimal amount,
            Instant occurredAt
    ) {
        return new AbcTransaction(
                transactionId,
                eventType,
                amount,
                occurredAt
        );
    }
}
