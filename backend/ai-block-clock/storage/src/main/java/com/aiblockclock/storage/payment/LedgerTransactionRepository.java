package com.aiblockclock.storage.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerTransactionRepository
        extends JpaRepository<LedgerTransactionEntity, Long> {

    Optional<LedgerTransactionEntity> findByIdempotencyKey(
            String idempotencyKey
    );

    Optional<LedgerTransactionEntity> findByChainIdAndTransactionHash(
            Long chainId,
            String transactionHash
    );
}