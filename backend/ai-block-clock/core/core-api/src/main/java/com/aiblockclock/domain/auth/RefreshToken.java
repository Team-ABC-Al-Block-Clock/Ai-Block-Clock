package com.aiblockclock.domain.auth;

import java.time.Instant;

public record RefreshToken(
        RefreshTokenId id,
        LoginSessionId sessionId,
        String refreshTokenHash,
        Instant expiresAt,
        Instant usedAt
) {
}
