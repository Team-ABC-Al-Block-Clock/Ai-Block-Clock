package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LedgerAccountRepository
        extends JpaRepository<LedgerAccountEntity, Long> {

    Optional<LedgerAccountEntity> findByAccountTypeAndReferenceId(
            LedgerAccountType accountType,
            String referenceId
    );
}