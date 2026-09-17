package com.aiblockclock.domain.auth;

public record RefreshTokenId(long value) {
    public static RefreshTokenId of(long value) {
        return new RefreshTokenId(value);
    }
}
