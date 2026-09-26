package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserReader userReader;
    private final AbcBalanceReader abcBalanceReader;
    private final WithdrawalReader withdrawalReader;
    private final WithdrawalWriter withdrawalWriter;
    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final WithdrawalProperties withdrawalProperties;
    private final Clock clock;

    // 처리 중인 출금 · 신청 주기 · 정산금을 본 값 그대로 믿고 묶으려고 잠근 뒤에 읽는다(lockRequester).
    // 같은 요청 번호로 이미 신청했으면 다시 묶지 않고 그 신청을 돌려준다.
    @Transactional
    public Withdrawal request(UserId requester, WithdrawalRequestId requestId, AbcAmount amount) {
        User user = lockRequester(requester);
        Optional<Withdrawal> earlier = withdrawalReader.find(requester, requestId);

        if (earlier.isPresent()) {
            return earlier.get();
        }

        withdrawalProperties.policy().ensureAllowed(amount, abcBalanceReader.read(requester).payout(),
                withdrawalReader.readRecent(requester), Instant.now(clock));

        Withdrawal requested = withdrawalWriter.append(
                Withdrawal.requested(requestId, requester, amount, user.walletAddress()));
        ledgerTransactionWriter.append(
                LedgerTransaction.forWithdrawalHold(requested.getId(), requester, requested.getAmount()));

        return requested;
    }

    // 심사를 기다리는 신청을 거두고 묶은 금액을 정산금으로 돌려놓는다. 둘이 한 트랜잭션이라 한쪽만 되는 일이 없다.
    // 신청과 같은 잠금으로 줄을 세워, 연타로 두 취소가 겹쳐도 늦은 쪽은 앞 취소가 끝난 상태를 읽고 그대로 돌려받는다.
    @Transactional
    public Withdrawal cancel(UserId requester, Long withdrawalId) {
        lockRequester(requester);

        Withdrawal withdrawal = mine(withdrawalId, requester);

        if (withdrawal.isCanceled()) {
            return withdrawal;
        }

        withdrawal.cancel();

        Withdrawal canceled = withdrawalWriter.changeStatus(withdrawal, WithdrawalStatus.REQUESTED);
        ledgerTransactionWriter.append(
                LedgerTransaction.forWithdrawalRelease(canceled.getId(), requester, canceled.getAmount()));

        return canceled;
    }

    // 사용자 행을 잠가 한 사람의 신청 · 취소를 한 줄로 세운다. 잠금은 트랜잭션의 첫 조회여야 한다 —
    // MySQL 은 첫 일반 조회 때 읽는 기준 시점을 정하므로, 잠금을 얻은 뒤에 읽어야 앞 요청이 커밋한 출금과 정산금이 보인다.
    private User lockRequester(UserId requester) {
        return userReader.findByIdForUpdate(requester)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
    }

    // 남의 신청은 있다는 것도 알리지 않는다.
    private Withdrawal mine(Long withdrawalId, UserId requester) {
        Withdrawal withdrawal = withdrawalReader.read(withdrawalId);

        if (!withdrawal.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.WITHDRAWAL_NOT_FOUND);
        }

        return withdrawal;
    }
}
