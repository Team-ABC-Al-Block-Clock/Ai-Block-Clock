package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AbcBalanceReader {

    private final LedgerAccountRepository ledgerAccountRepository;

    @Transactional(readOnly = true)
    public AbcBalance read(String userReferenceId) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(
                        LedgerAccountType.ABC_BALANCE,
                        userReferenceId
                )
                .map(LedgerAccountEntity::getBalance)
                .map(AbcBalance::from)
                .orElseGet(AbcBalance::zero);
    }
}
