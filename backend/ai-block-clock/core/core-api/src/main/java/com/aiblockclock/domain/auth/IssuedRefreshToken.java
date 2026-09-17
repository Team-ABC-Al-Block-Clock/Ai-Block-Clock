package com.aiblockclock.domain.auth;

import java.time.Instant;

public record IssuedRefreshToken(
        String value,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "IssuedRefreshToken[value=***, expiresAt=%s]".formatted(expiresAt);
    }
}
