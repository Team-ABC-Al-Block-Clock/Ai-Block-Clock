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

    public static Withdrawal requested(
            WithdrawalRequestId requestId,
            UserId requester,
            AbcAmount amount,
            String walletAddress
    ) {
        return new Withdrawal(null, requestId, requester, amount, walletAddress, WithdrawalStatus.REQUESTED, null);
    }

    public static Withdrawal restore(
            Long id,
            WithdrawalRequestId requestId,
            UserId requester,
            AbcAmount amount,
            String walletAddress,
            WithdrawalStatus status,
            Instant requestedAt
    ) {
        return new Withdrawal(id, requestId, requester, amount, walletAddress, status, requestedAt);
    }

    public boolean isRequestedBy(UserId candidate) {
        return requester.equals(candidate);
    }

    public boolean isCanceled() {
        return status == WithdrawalStatus.CANCELED;
    }

    // 심사를 기다리는 신청만 사용자가 거둘 수 있다. 승인된 뒤에는 지급이 진행 중이라 멈출 수 없다
    public void cancel() {
        if (status != WithdrawalStatus.REQUESTED) {
            throw new CoreException(ErrorType.WITHDRAWAL_INVALID_STATE);
        }

        status = WithdrawalStatus.CANCELED;
    }
}
