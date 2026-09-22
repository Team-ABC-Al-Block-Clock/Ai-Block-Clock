package com.aiblockclock.domain.payment;

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

    public AbcTransactionPage readTransactions(
            String userReferenceId,
            AbcTransactionSearchCondition condition
    ) {
        return abcTransactionReader.readPage(
                userReferenceId,
                condition
        );
    }
}
