package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface LedgerAccountRepository
        extends JpaRepository<LedgerAccountEntity, Long> {

    Optional<LedgerAccountEntity> findByAccountTypeAndReferenceId(
            LedgerAccountType accountType,
            String referenceId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO ledger_account (account_type, reference_id, balance, version, created_at, updated_at)
            VALUES (:accountType, :referenceId, 0.000000, 0, :createdAt, :createdAt)
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("accountType") String accountType,
            @Param("referenceId") String referenceId,
            @Param("createdAt") Instant createdAt
    );

    @Query(value = """
            SELECT id FROM ledger_account
             WHERE account_type = :accountType AND reference_id = :referenceId
             FOR UPDATE
            """, nativeQuery = true)
    Optional<Long> findIdForBalanceUpdate(
            @Param("accountType") String accountType,
            @Param("referenceId") String referenceId
    );

    @Query(value = """
            SELECT balance FROM ledger_account
             WHERE account_type = :accountType AND reference_id = :referenceId
             FOR UPDATE
            """, nativeQuery = true)
    Optional<BigDecimal> findBalanceForUpdate(
            @Param("accountType") String accountType,
            @Param("referenceId") String referenceId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE LedgerAccountEntity account
               SET account.balance = account.balance + :amountDelta,
                   account.version = account.version + 1,
                   account.updatedAt = :updatedAt
             WHERE account.id = :accountId
            """)
    int addBalanceDelta(
            @Param("accountId") Long accountId,
            @Param("amountDelta") BigDecimal amountDelta,
            @Param("updatedAt") Instant updatedAt
    );
}
