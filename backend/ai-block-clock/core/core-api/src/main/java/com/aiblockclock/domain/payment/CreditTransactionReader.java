package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreditTransactionReader {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public List<CreditTransaction> readAll(
            String userReferenceId
    ) {
        return ledgerEntryRepository
                .findAllByAccountAccountTypeAndAccountReferenceIdOrderByTransactionCreatedAtDescTransactionIdDesc(
                        LedgerAccountType.ABC_BALANCE,
                        userReferenceId
                )
                .stream()
                .map(this::toCreditTransaction)
                .toList();
    }

    private CreditTransaction toCreditTransaction(
            LedgerEntryEntity entry
    ) {
        return CreditTransaction.of(
                entry.getTransaction().getId(),
                entry.getTransaction().getEventType(),
                entry.getAmountDelta(),
                entry.getTransaction().getCreatedAt()
        );
    }
}
