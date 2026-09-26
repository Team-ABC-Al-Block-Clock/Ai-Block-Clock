package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.WithdrawalStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 체인이 답한 결과로 출금을 닫는 한 단계다. 상태를 바꾸는 것과 장부를 적는 것이 한 트랜잭션이라,
// 한쪽만 되어 "지급했는데 장부엔 묶여 있음" 이나 그 반대가 되지 않는다(충전 확정 ChargeConfirmationManager 와 같은 모양).
@Component
@RequiredArgsConstructor
public class WithdrawalPayoutManager {

    private final WithdrawalWriter withdrawalWriter;
    private final LedgerTransactionWriter ledgerTransactionWriter;

    // 성공을 확인했다. 묶은 금액을 장부 밖으로 내보낸다
    @Transactional
    public Withdrawal complete(Withdrawal sent, long chainId) {
        WithdrawalStatus readStatus = sent.getStatus();

        sent.completePayout();

        Withdrawal paid = withdrawalWriter.completePayout(sent, readStatus);
        ledgerTransactionWriter.append(LedgerTransaction.forWithdrawalPayout(paid, chainId));

        return paid;
    }

    // 체인이 되돌렸다. 토큰이 나가지 않았으므로 묶은 금액을 정산금으로 돌려놓는다.
    // 취소 · 반려와 같은 키라 한 신청에 한 번만 풀린다
    @Transactional
    public Withdrawal fail(Withdrawal sent) {
        WithdrawalStatus readStatus = sent.getStatus();

        sent.failPayout();

        Withdrawal failed = withdrawalWriter.changeStatus(sent, readStatus);
        ledgerTransactionWriter.append(LedgerTransaction.forWithdrawalRelease(
                failed.getId(), failed.getRequester(), failed.getAmount()));

        return failed;
    }
}
