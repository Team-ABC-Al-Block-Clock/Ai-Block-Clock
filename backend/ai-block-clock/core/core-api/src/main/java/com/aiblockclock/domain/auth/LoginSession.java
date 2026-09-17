package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.time.Instant;

public record LoginSession(
        LoginSessionId id,
        UserId userId,
        Instant expiresAt,
        Instant revokedAt
) {
    public void checkUsable(Instant now) {
        if (!now.isBefore(expiresAt)) {
            throw new CoreException(ErrorType.LOGIN_SESSION_EXPIRED);
        }

        if (revokedAt != null) {
            throw new CoreException(ErrorType.LOGIN_SESSION_REVOKED);
        }
    }
}
