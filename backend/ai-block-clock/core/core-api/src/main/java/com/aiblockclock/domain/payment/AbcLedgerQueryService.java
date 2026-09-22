package com.aiblockclock.domain.payment;

import com.aiblockclock.support.paging.CursorPage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AbcLedgerQueryService {

    private final AbcBalanceReader abcBalanceReader;
    private final AbcTransactionReader abcTransactionReader;

    public AbcBalance readBalance(String userReferenceId) {
        return abcBalanceReader.read(userReferenceId);
    }

    public CursorPage<AbcTransaction> readTransactions(
            String userReferenceId,
            AbcTransactionSearchCondition condition,
            String cursor,
            int size
    ) {
        return abcTransactionReader.readPage(userReferenceId, condition, cursor, size);
    }
}
