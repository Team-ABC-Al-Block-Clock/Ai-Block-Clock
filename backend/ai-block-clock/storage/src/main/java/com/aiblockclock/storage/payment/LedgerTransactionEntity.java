package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerEventType;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ledger_transaction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30, updatable = false)
    private LedgerEventType eventType;

    @Column(name = "idempotency_key", nullable = false, length = 150, updatable = false)
    private String idempotencyKey;

    @Column(name = "reference_type", nullable = false, length = 30, updatable = false)
    private String referenceType;

    @Column(name = "reference_id", nullable = false, length = 100, updatable = false)
    private String referenceId;

    @Column(name = "chain_id", updatable = false)
    private Long chainId;

    @Column(name = "transaction_hash", length = 66, updatable = false)
    private String transactionHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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