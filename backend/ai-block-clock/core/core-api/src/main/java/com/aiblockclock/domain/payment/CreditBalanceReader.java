package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CreditBalanceReader {

    private final LedgerAccountRepository ledgerAccountRepository;

    @Transactional(readOnly = true)
    public CreditBalance read(String userReferenceId) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(
                        LedgerAccountType.ABC_BALANCE,
                        userReferenceId
                )
                .map(LedgerAccountEntity::getBalance)
                .map(CreditBalance::from)
                .orElseGet(CreditBalance::zero);
    }
}
