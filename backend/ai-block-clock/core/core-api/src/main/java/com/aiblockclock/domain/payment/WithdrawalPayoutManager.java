package com.aiblockclock.domain.payment;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 체인에서 성공을 확인한 출금을 끝내는 한 단계다. 상태를 PAID 로 바꾸는 것과 묶은 금액을 장부에서 내보내는 것이
// 한 트랜잭션이라, 한쪽만 되어 "지급했는데 장부엔 묶여 있음" 이나 그 반대가 되지 않는다(충전 확정 ChargeConfirmationManager 와 같은 모양).
@Component
@RequiredArgsConstructor
public class WithdrawalPayoutManager {

    private final WithdrawalWriter withdrawalWriter;
    private final LedgerTransactionWriter ledgerTransactionWriter;

    @Transactional
    public Withdrawal complete(Withdrawal paying, long chainId) {
        paying.completePayout();

        Withdrawal paid = withdrawalWriter.completePayout(paying);
        ledgerTransactionWriter.append(LedgerTransaction.forWithdrawalPayout(paid, chainId));

        return paid;
    }
}
