package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ChargeRequest {

    private final Long id;
    private final String userReferenceId;
    private final String senderWalletAddress;
    private final Long chainId;
    private final String transactionHash;
    private final BigDecimal requestedUsdcAmount;

    private BigDecimal creditedAbcAmount;
    private ChargeStatus status;
    private ChargeStatusReason statusReason;
    private Instant confirmedAt;

    private final Long version;

    public static ChargeRequest pending(
            String userReferenceId,
            String senderWalletAddress,
            Long chainId,
            String transactionHash,
            BigDecimal requestedUsdcAmount
    ) {
        return new ChargeRequest(
                null,
                userReferenceId,
                senderWalletAddress,
                chainId,
                transactionHash,
                requestedUsdcAmount,
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
            BigDecimal requestedUsdcAmount,
            BigDecimal creditedAbcAmount,
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
                requestedUsdcAmount,
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
            BigDecimal creditedAbcAmount,
            Instant confirmedAt
    ) {
        ensurePending();

        if (creditedAbcAmount == null
                || creditedAbcAmount.signum() <= 0) {
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
