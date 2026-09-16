package com.aiblockclock.domain.auth;

import java.time.Instant;

public record WalletAuthMessage(
        String walletAddress,
        String nonce,
        String message,
        Instant issuedAt,
        Instant expiresAt,
        Instant usedAt
) {
}
