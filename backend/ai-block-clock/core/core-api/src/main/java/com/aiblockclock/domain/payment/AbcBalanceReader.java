package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
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
    public AbcBalance read(UserId userId) {
        String userReferenceId = referenceIdOf(userId);
        BigDecimal available = balanceOf(LedgerAccountType.ABC_BALANCE, userReferenceId);
        BigDecimal reserved = balanceOf(LedgerAccountType.EXECUTION_ESCROW, userReferenceId);

        return AbcBalance.of(available, reserved);
    }

    // 장부 계정은 가입 때 LedgerAccountWriter 가 같은 규칙(사용자 ID 의 십진 문자열)으로 만든다.
    private static String referenceIdOf(UserId userId) {
        return Long.toString(userId.value());
    }

    // 계정 행은 첫 거래 때 만들어지므로, 아직 없는 계정은 잔액 0 으로 본다.
    private BigDecimal balanceOf(LedgerAccountType accountType, String userReferenceId) {
        return ledgerAccountRepository
                .findByAccountTypeAndReferenceId(accountType, userReferenceId)
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }
}
