package com.aiblockclock.domain.auth;

import java.time.Instant;

public record IssuedSessionToken(
        String value,
        Instant expiresAt
) {
    @Override
    public String toString() {
        return "IssuedSessionToken[value=***, expiresAt=%s]".formatted(expiresAt);
    }
}
