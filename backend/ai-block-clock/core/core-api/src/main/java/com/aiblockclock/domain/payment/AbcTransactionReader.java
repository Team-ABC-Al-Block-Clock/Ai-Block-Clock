package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AbcTransactionReader {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional(readOnly = true)
    public CursorPage<AbcTransaction> readPage(
            String userReferenceId,
            AbcTransactionSearchCondition condition,
            String cursor,
            int size
    ) {
        int offset = OffsetCursor.decode(cursor);

        // size + 1 개를 읽어 하나 더 나오면 다음 페이지가 있다고 본다. 전체 개수를 세지 않기 위함이다.
        List<LedgerEntryEntity> found = ledgerEntryRepository.searchAbcTransactions(
                LedgerAccountType.ABC_BALANCE,
                userReferenceId,
                condition.eventType(),
                condition.period().startAtInclusive(),
                condition.period().endAtExclusive(),
                OffsetPageable.of(offset, size + 1)
        );

        boolean hasNext = found.size() > size;
        List<AbcTransaction> content = found.stream()
                .limit(size)
                .map(this::toAbcTransaction)
                .toList();

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
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
