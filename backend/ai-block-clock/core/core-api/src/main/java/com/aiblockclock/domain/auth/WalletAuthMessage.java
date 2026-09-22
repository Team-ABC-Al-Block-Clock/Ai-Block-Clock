package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;

public record WalletAuthMessage(
        String walletAddress,
        String nonce,
        String message,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt
) {
    public void checkUsable(Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new CoreException(ErrorType.WALLET_AUTH_MESSAGE_EXPIRED);
        }

        if (usedAt != null) {
            throw new CoreException(ErrorType.WALLET_AUTH_MESSAGE_ALREADY_USED);
        }
    }
}
