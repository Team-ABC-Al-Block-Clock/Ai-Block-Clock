package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AbcTransactionReader {

    private static final int FIRST_PAGE = 1;
    private static final int PAGE_SIZE = 10;

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public AbcTransactionPage readPage(
            String userReferenceId,
            AbcTransactionSearchCondition condition
    ) {
        Page<LedgerEntryEntity> entries =
                ledgerEntryRepository
                        .searchAbcTransactions(
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

        List<AbcTransaction> items = entries
                .stream()
                .map(this::toAbcTransaction)
                .toList();

        return AbcTransactionPage.of(
                items,
                entries.getNumber() + FIRST_PAGE,
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages(),
                entries.hasPrevious(),
                entries.hasNext()
        );
    }

    private AbcTransaction toAbcTransaction(
            LedgerEntryEntity entry
    ) {
        LedgerTransactionEntity transaction = entry.getTransaction();

        return AbcTransaction.of(
                transaction.getId(),
                transaction.getEventType(),
                entry.getAmountDelta(),
                transaction.getCreatedAt(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getTransactionHash()
        );
    }
}
