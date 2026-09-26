package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class Withdrawal {

    private final Long id;
    private final WithdrawalRequestId requestId;
    private final UserId requester;
    private final AbcAmount amount;
    // 신청할 때의 로그인 지갑이다. 나중에 지갑이 바뀌어도 신청한 곳으로 보낸다
    private final String walletAddress;
    private WithdrawalStatus status;
    // 저장된 뒤에만 있다. 신청 주기를 이 시각부터 잰다
    private final Instant requestedAt;
    // 아래 셋은 관리자가 심사한 뒤에만 있다. 심사 시각은 Writer 가 적는다
    private UserId reviewer;
    private final Instant reviewedAt;
    private String rejectReason;
    // 아래 셋은 지급을 시작한 뒤에만 있다. 보낸 시각 · 끝난 시각은 Writer 가 적는다
    private PayoutTransaction payoutTransaction;
    private final Instant payoutSubmittedAt;
    private final Instant paidAt;

    public static Withdrawal requested(
            WithdrawalRequestId requestId,
            UserId requester,
            AbcAmount amount,
            String walletAddress
    ) {
        return new Withdrawal(null, requestId, requester, amount, walletAddress, WithdrawalStatus.REQUESTED,
                null, null, null, null, null, null, null);
    }

    public static Withdrawal restore(
            Long id,
            WithdrawalRequestId requestId,
            UserId requester,
            AbcAmount amount,
            String walletAddress,
            WithdrawalStatus status,
            Instant requestedAt,
            UserId reviewer,
            Instant reviewedAt,
            String rejectReason,
            PayoutTransaction payoutTransaction,
            Instant payoutSubmittedAt,
            Instant paidAt
    ) {
        return new Withdrawal(id, requestId, requester, amount, walletAddress, status, requestedAt,
                reviewer, reviewedAt, rejectReason, payoutTransaction, payoutSubmittedAt, paidAt);
    }

    public boolean isRequestedBy(UserId candidate) {
        return requester.equals(candidate);
    }

    public boolean isCanceled() {
        return status == WithdrawalStatus.CANCELED;
    }

    public boolean isApproved() {
        return status == WithdrawalStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == WithdrawalStatus.REJECTED;
    }

    public boolean isPaid() {
        return status == WithdrawalStatus.PAID;
    }

    // 심사를 기다리는 신청만 사용자가 거둘 수 있다. 승인된 뒤에는 지급이 진행 중이라 멈출 수 없다
    public void cancel() {
        checkRequested();

        status = WithdrawalStatus.CANCELED;
    }

    // 승인하면 지급(PAY-025)을 기다린다. 금액은 지급이 끝날 때까지 출금 예치에 묶여 있다
    public void approve(UserId approver) {
        checkRequested();

        status = WithdrawalStatus.APPROVED;
        reviewer = approver;
    }

    // 반려하면 묶은 금액을 정산금으로 돌려놓아야 한다. 돌려놓는 장부 거래는 Service 가 같은 트랜잭션에서 적는다
    public void reject(UserId rejecter, String reason) {
        checkRequested();

        status = WithdrawalStatus.REJECTED;
        reviewer = rejecter;
        rejectReason = reason;
    }

    // 서명한 전송을 보내기 전에 해시와 함께 지급 중으로 적는다. 보내고 나서 적으면, 그 사이 서버가 멈췄을 때 보냈는지 알 길이 없다
    public void startPayout(PayoutTransaction transaction) {
        checkStatus(WithdrawalStatus.APPROVED);

        status = WithdrawalStatus.PAYING;
        payoutTransaction = transaction;
    }

    // 체인에서 성공을 확인한 뒤에만 끝낸다. 묶은 금액을 내보내는 장부 거래는 같은 트랜잭션에서 적는다
    public void completePayout() {
        checkStatus(WithdrawalStatus.PAYING);

        status = WithdrawalStatus.PAID;
    }

    // 취소와 심사는 모두 심사 대기에서만 한다. 한 번 닫히거나 승인된 신청은 되돌리지 않는다
    private void checkRequested() {
        checkStatus(WithdrawalStatus.REQUESTED);
    }

    private void checkStatus(WithdrawalStatus expected) {
        if (status != expected) {
            throw new CoreException(ErrorType.WITHDRAWAL_INVALID_STATE);
        }
    }
}
