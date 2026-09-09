package com.aiblockclock.storage.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntryEntity, Long> {

    List<LedgerEntryEntity> findAllByTransactionIdOrderByIdAsc(
            Long transactionId
    );

    List<LedgerEntryEntity> findAllByAccountIdOrderByIdAsc(
            Long accountId
    );
}