package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.identity.IdentityVerification;
import com.aiblockclock.domain.identity.IdentityVerificationReader;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserReader userReader;
    private final AbcBalanceReader abcBalanceReader;
    private final WithdrawalReader withdrawalReader;
    private final WithdrawalWriter withdrawalWriter;
    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final WithdrawalProperties withdrawalProperties;
    private final IdentityVerificationReader identityVerificationReader;

    // 사용자 행을 잠가 한 사람의 신청을 한 줄로 세운다. 잠금은 이 트랜잭션의 첫 조회여야 한다 —
    // MySQL 은 첫 일반 조회 때 읽는 기준 시점을 정하므로, 잠금을 얻은 뒤에 읽어야 앞 신청이 커밋한 출금과 정산금이 보인다.
    // 그래야 처리 중인 출금 · 신청 주기 · 정산금을 본 값 그대로 믿고 묶을 수 있다.
    // 같은 요청 번호로 이미 신청했으면 다시 묶지 않고 그 신청을 돌려준다.
    @Transactional
    public Withdrawal request(UserId requester, WithdrawalRequestId requestId, AbcAmount amount) {
        User user = userReader.findByIdForUpdate(requester)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
        Optional<Withdrawal> earlier = withdrawalReader.find(requester, requestId);

        if (earlier.isPresent()) {
            return earlier.get();
        }

        // 지갑 서명은 지갑의 주인까지만 말해 준다. 돈이 밖으로 나가기 전에 그 뒤의 사람을 관리자가 확인했어야 한다(ACC-004)
        if (!identityVerificationReader.isApproved(requester)) {
            throw new CoreException(ErrorType.WITHDRAWAL_IDENTITY_NOT_VERIFIED);
        }

        withdrawalProperties.policy().ensureAllowed(
                amount, abcBalanceReader.read(requester).payout(), withdrawalReader.readRecent(requester), Instant.now());

        Withdrawal requested = withdrawalWriter.append(
                Withdrawal.requested(requestId, requester, amount, user.walletAddress()));
        ledgerTransactionWriter.append(
                LedgerTransaction.forWithdrawalHold(requested.getId(), requester, requested.getAmount()));

        return requested;
    }

    // 심사를 기다리는 신청을 거두고 묶은 금액을 정산금으로 돌려놓는다. 둘이 한 트랜잭션이라 한쪽만 되는 일이 없다.
    // 이미 취소한 신청이면 그대로 돌려준다. 같은 취소가 다시 와도(재시도 · 연타) 실패로 보이지 않는다.
    @Transactional
    public Withdrawal cancel(UserId requester, Long withdrawalId) {
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

    // 관리자가 볼 심사 대기 목록이다. 신청한 사람들의 본인 확인을 한 번에 읽어 짝지어 준다.
    @Transactional(readOnly = true)
    public List<PendingWithdrawal> readAllPending() {
        List<Withdrawal> requested = withdrawalReader.readAllRequested();
        Map<UserId, IdentityVerification> identities = identityVerificationReader.readAllByUser(
                requested.stream().map(Withdrawal::getRequester).collect(Collectors.toSet()));

        return requested.stream()
                .map(withdrawal -> new PendingWithdrawal(withdrawal, identities.get(withdrawal.getRequester())))
                .toList();
    }

    // 승인하면 지급(PAY-025)을 기다린다. 금액은 계속 묶여 있고 체인에는 아직 아무것도 보내지 않는다.
    // 이미 승인했으면 그대로 돌려준다. 같은 승인이 다시 와도(재시도 · 연타) 실패로 보이지 않는다.
    @Transactional
    public Withdrawal approve(UserId approver, Long withdrawalId) {
        Withdrawal withdrawal = withdrawalReader.read(withdrawalId);

        if (withdrawal.isApproved()) {
            return withdrawal;
        }

        withdrawal.approve(approver);

        return withdrawalWriter.modifyReview(withdrawal);
    }

    // 반려를 적고 묶은 금액을 정산금으로 돌려놓는다. 둘이 한 트랜잭션이라 한쪽만 되는 일이 없다.
    // 돌려놓는 거래는 취소와 같은 키를 써서, 취소와 반려가 겹쳐도 한 신청에 한 번만 풀린다.
    // 이미 반려했으면 그대로 돌려준다. 사유는 처음 반려한 것이 남는다.
    @Transactional
    public Withdrawal reject(UserId rejecter, Long withdrawalId, String reason) {
        Withdrawal withdrawal = withdrawalReader.read(withdrawalId);

        if (withdrawal.isRejected()) {
            return withdrawal;
        }

        withdrawal.reject(rejecter, reason);

        Withdrawal rejected = withdrawalWriter.modifyReview(withdrawal);
        ledgerTransactionWriter.append(LedgerTransaction.forWithdrawalRelease(
                rejected.getId(), rejected.getRequester(), rejected.getAmount()));

        return rejected;
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
