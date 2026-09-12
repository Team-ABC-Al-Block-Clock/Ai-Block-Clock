package com.aiblockclock.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditBalanceReader creditBalanceReader;
    private final CreditTransactionReader creditTransactionReader;

    public CreditBalance readBalance(String userReferenceId) {
        return creditBalanceReader.read(userReferenceId);
    }

    public List<CreditTransaction> readTransactions(
            String userReferenceId
    ) {
        return creditTransactionReader.readAll(userReferenceId);
    }
}
