package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntryEntity, Long> {

    List<LedgerEntryEntity> findAllByTransactionIdOrderByIdAsc(
            Long transactionId
    );

    List<LedgerEntryEntity> findAllByAccountIdOrderByIdAsc(
            Long accountId
    );

    @EntityGraph(attributePaths = "transaction")
    @Query("""
            SELECT entry
            FROM LedgerEntryEntity entry
            JOIN entry.transaction ledgerTransaction
            WHERE entry.account.accountType = :accountType
              AND entry.account.referenceId = :referenceId
              AND (
                    :eventType IS NULL
                    OR ledgerTransaction.eventType = :eventType
                  )
              AND (
                    :startAtInclusive IS NULL
                    OR ledgerTransaction.createdAt >= :startAtInclusive
                  )
              AND (
                    :endAtExclusive IS NULL
                    OR ledgerTransaction.createdAt < :endAtExclusive
                  )
            ORDER BY ledgerTransaction.createdAt DESC,
                     ledgerTransaction.id DESC
            """)
    Page<LedgerEntryEntity>
    searchCreditTransactions(
            @Param("accountType")
            LedgerAccountType accountType,
            @Param("referenceId")
            String referenceId,
            @Param("eventType")
            LedgerEventType eventType,
            @Param("startAtInclusive")
            Instant startAtInclusive,
            @Param("endAtExclusive")
            Instant endAtExclusive,
            Pageable pageable
    );
}
