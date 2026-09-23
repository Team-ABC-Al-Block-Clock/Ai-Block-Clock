package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class AbcBalanceReader {

    private final LedgerAccountRepository ledgerAccountRepository;

    @Transactional(readOnly = true)
    public AbcBalance read(String userReferenceId) {
        BigDecimal available = balanceOf(LedgerAccountType.ABC_BALANCE, userReferenceId);
        BigDecimal reserved = balanceOf(LedgerAccountType.EXECUTION_ESCROW, userReferenceId);

        return AbcBalance.of(available, reserved);
    }

    // 계정 행은 첫 거래 때 만들어지므로, 아직 없는 계정은 잔액 0 으로 본다.
    private BigDecimal balanceOf(LedgerAccountType accountType, String userReferenceId) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(accountType, userReferenceId)
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }
}
