package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerEventType;

import java.math.BigDecimal;
import java.time.Instant;

// 내역 한 줄. referenceType·referenceId 는 이 거래가 무엇 때문에 생겼는지(충전 요청·실행),
// transactionHash 는 블록체인에 대응하는 거래가 있을 때(충전)만 값이 있다.
public record AbcTransaction(
        Long transactionId,
        LedgerEventType eventType,
        BigDecimal amount,
        Instant occurredAt,
        String referenceType,
        String referenceId,
        String transactionHash
) {

    public static AbcTransaction of(
            Long transactionId,
            LedgerEventType eventType,
            BigDecimal amount,
            Instant occurredAt,
            String referenceType,
            String referenceId,
            String transactionHash
    ) {
        return new AbcTransaction(
                transactionId,
                eventType,
                amount,
                occurredAt,
                referenceType,
                referenceId,
                transactionHash
        );
    }
}
