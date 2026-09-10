package com.aiblockclock.storage.payment;

import com.aiblockclock.storage.audit.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "ledger_entry")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntryEntity extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ledger_transaction_id",
            nullable = false,
            updatable = false
    )
    private LedgerTransactionEntity transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ledger_account_id",
            nullable = false,
            updatable = false
    )
    private LedgerAccountEntity account;

    @Column(
            nullable = false,
            precision = 38,
            scale = 3,
            updatable = false
    )
    private BigDecimal amountDelta;

    public static LedgerEntryEntity of(
            LedgerTransactionEntity transaction,
            LedgerAccountEntity account,
            BigDecimal amountDelta
    ) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.transaction = transaction;
        entity.account = account;
        entity.amountDelta = amountDelta;
        return entity;
    }
}
