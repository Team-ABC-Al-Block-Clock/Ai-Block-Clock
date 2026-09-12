package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CreditTransactionReader {

    private static final int FIRST_PAGE = 1;
    private static final int PAGE_SIZE = 10;

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public CreditTransactionPage readPage(
            String userReferenceId,
            CreditTransactionSearchCondition condition
    ) {
        Page<LedgerEntryEntity> entries =
                ledgerEntryRepository
                        .searchCreditTransactions(
                                LedgerAccountType.ABC_BALANCE,
                                userReferenceId,
                                condition.eventType(),
                                condition.startAtInclusive(),
                                condition.endAtExclusive(),
                                PageRequest.of(
                                        condition.page()
                                                - FIRST_PAGE,
                                        PAGE_SIZE
                                )
                        );

        List<CreditTransaction> items = entries
                .stream()
                .map(this::toCreditTransaction)
                .toList();

        return CreditTransactionPage.of(
                items,
                entries.getNumber() + FIRST_PAGE,
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages(),
                entries.hasPrevious(),
                entries.hasNext()
        );
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
