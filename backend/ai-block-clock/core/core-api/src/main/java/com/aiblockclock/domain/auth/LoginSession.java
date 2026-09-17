package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;

import java.time.Instant;

public record LoginSession(
        LoginSessionId id,
        UserId userId,
        Instant expiresAt,
        Instant revokedAt
) {
}
