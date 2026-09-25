package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChargeRequest {

    private final Long id;
    private final String userReferenceId;
    private final String senderWalletAddress;
    private final Long chainId;
    private final String transactionHash;
    private final DepositAmount requestedDepositAmount;
    // 저장된 뒤에만 있다. 대기 한도를 이 시각부터 잰다
    private final Instant requestedAt;

    private AbcAmount creditedAbcAmount;
    private ChargeStatus status;
    private ChargeStatusReason statusReason;
    private Instant confirmedAt;

    private final Long version;

    public static ChargeRequest pending(
            String userReferenceId,
            String senderWalletAddress,
            Long chainId,
            String transactionHash,
            DepositAmount requestedDepositAmount
    ) {
        return new ChargeRequest(
                null,
                userReferenceId,
                senderWalletAddress,
                chainId,
                transactionHash,
                requestedDepositAmount,
                null,
                null,
                ChargeStatus.PENDING,
                null,
                null,
                null
        );
    }

    public static ChargeRequest restore(
            Long id,
            String userReferenceId,
            String senderWalletAddress,
            Long chainId,
            String transactionHash,
            DepositAmount requestedDepositAmount,
            Instant requestedAt,
            AbcAmount creditedAbcAmount,
            ChargeStatus status,
            ChargeStatusReason statusReason,
            Instant confirmedAt,
            Long version
    ) {
        return new ChargeRequest(
                id,
                userReferenceId,
                senderWalletAddress,
                chainId,
                transactionHash,
                requestedDepositAmount,
                requestedAt,
                creditedAbcAmount,
                status,
                statusReason,
                confirmedAt,
                version
        );
    }

    public boolean isPending() {
        return status == ChargeStatus.PENDING;
    }

    // 요청을 받고 한도가 지나도록 거래가 블록에 적히지 않았다. 사용자가 보내지 않았거나 거래가 사라진 경우다
    public boolean isVerificationOverdue(Instant now, Duration timeout) {
        return requestedAt != null && !now.isBefore(requestedAt.plus(timeout));
    }

    public boolean isOwnedBy(String candidateUserReferenceId) {
        return userReferenceId.equals(candidateUserReferenceId);
    }

    public void ensureOwnedBy(String candidateUserReferenceId) {
        if (!isOwnedBy(candidateUserReferenceId)) {
            throw new CoreException(
                    ErrorType.CHARGE_REQUEST_FORBIDDEN
            );
        }
    }

    public void confirm(
            AbcAmount creditedAbcAmount,
            Instant confirmedAt
    ) {
        ensurePending();

        if (creditedAbcAmount == null) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_AMOUNT
            );
        }

        if (confirmedAt == null) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_STATE
            );
        }

        this.creditedAbcAmount = creditedAbcAmount;
        status = ChargeStatus.CONFIRMED;
        statusReason = null;
        this.confirmedAt = confirmedAt;
    }

    public void fail(ChargeStatusReason failureReason) {
        ensurePending();

        if (failureReason == null
                || failureReason == ChargeStatusReason.USER_CANCELED) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_STATE
            );
        }

        status = ChargeStatus.FAILED;
        statusReason = failureReason;
        creditedAbcAmount = null;
        confirmedAt = null;
    }

    public void cancel() {
        if (status == ChargeStatus.CANCELED) {
            return;
        }

        ensurePending();

        status = ChargeStatus.CANCELED;
        statusReason = ChargeStatusReason.USER_CANCELED;
        creditedAbcAmount = null;
        confirmedAt = null;
    }

    private void ensurePending() {
        if (!isPending()) {
            throw new CoreException(
                    ErrorType.CHARGE_INVALID_STATE
            );
        }
    }
}
