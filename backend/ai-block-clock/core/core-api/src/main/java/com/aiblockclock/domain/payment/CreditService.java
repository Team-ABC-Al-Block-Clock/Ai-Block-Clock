package com.aiblockclock.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditBalanceReader creditBalanceReader;
    private final CreditTransactionReader creditTransactionReader;

    public CreditBalance readBalance(String userReferenceId) {
        return creditBalanceReader.read(userReferenceId);
    }

    public CreditTransactionPage readTransactions(
            String userReferenceId,
            CreditTransactionSearchCondition condition
    ) {
        return creditTransactionReader.readPage(
                userReferenceId,
                condition
        );
    }
}
