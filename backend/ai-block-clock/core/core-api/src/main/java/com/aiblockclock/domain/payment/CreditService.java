package com.aiblockclock.domain.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditBalanceReader creditBalanceReader;

    public CreditBalance readBalance(String userReferenceId) {
        return creditBalanceReader.read(userReferenceId);
    }
}
