package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @EntityGraph(attributePaths = "transaction")
    Page<LedgerEntryEntity>
    findAllByAccountAccountTypeAndAccountReferenceIdOrderByTransactionCreatedAtDescTransactionIdDesc(
            LedgerAccountType accountType,
            String referenceId,
            Pageable pageable
    );
}
