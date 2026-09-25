package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
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

    // 쓸 수 있는 잔액 행을 잠그고 읽는다. 잠금 읽기라 다른 실행이 막 확정한 차감까지 보인다.
    // 일반 조회는 트랜잭션 첫 조회 시점의 값을 볼 수 있어, 동시에 들어온 실행이 같은 잔액을 두 번 쓴다.
    // 잠금은 트랜잭션이 끝날 때 풀리므로 트랜잭션 없이 부르면 여기서 바로 실패하게 한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public AbcBalance readForUpdate(UserId userId) {
        String userReferenceId = referenceIdOf(userId);
        BigDecimal available = ledgerAccountRepository
                .findBalanceForUpdate(LedgerAccountType.ABC_BALANCE.name(), userReferenceId)
                .orElse(BigDecimal.ZERO);

        return AbcBalance.of(available, balanceOf(LedgerAccountType.EXECUTION_ESCROW, userReferenceId));
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
