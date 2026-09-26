package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.audit.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ledger_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerTransactionEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private LedgerEventType eventType;

    @Column(nullable = false, length = 150, updatable = false)
    private String idempotencyKey;

    @Column(nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(nullable = false, length = 100, updatable = false)
    private String referenceId;

    @Column(updatable = false)
    private Long chainId;

    @Column(length = 66, updatable = false)
    private String transactionHash;

    public static LedgerTransactionEntity of(
            LedgerEventType eventType,
            String idempotencyKey,
            String referenceType,
            String referenceId,
            Long chainId,
            String transactionHash
    ) {
        LedgerTransactionEntity entity = new LedgerTransactionEntity();
        entity.eventType = eventType;
        entity.idempotencyKey = idempotencyKey;
        entity.referenceType = referenceType;
        entity.referenceId = referenceId;
        entity.chainId = chainId;
        entity.transactionHash = transactionHash;
        return entity;
    }
}
