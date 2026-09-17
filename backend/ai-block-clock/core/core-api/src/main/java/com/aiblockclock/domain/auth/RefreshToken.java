package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;

public record RefreshToken(
        RefreshTokenId id,
        LoginSessionId sessionId,
        String refreshTokenHash,
        Instant expiresAt,
        Instant usedAt
) {
    public void checkUsable(Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new CoreException(ErrorType.REFRESH_TOKEN_EXPIRED);
        }

        if (usedAt != null) {
            throw new CoreException(ErrorType.REFRESH_TOKEN_ALREADY_USED);
        }
    }
}
