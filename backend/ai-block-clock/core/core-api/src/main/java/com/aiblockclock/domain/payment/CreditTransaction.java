package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;

import java.math.BigDecimal;
import java.time.Instant;

public record CreditTransaction(
        Long transactionId,
        LedgerEventType eventType,
        BigDecimal amount,
        Instant occurredAt
) {

    public static CreditTransaction of(
            Long transactionId,
            LedgerEventType eventType,
            BigDecimal amount,
            Instant occurredAt
    ) {
        return new CreditTransaction(
                transactionId,
                eventType,
                amount,
                occurredAt
        );
    }
}
