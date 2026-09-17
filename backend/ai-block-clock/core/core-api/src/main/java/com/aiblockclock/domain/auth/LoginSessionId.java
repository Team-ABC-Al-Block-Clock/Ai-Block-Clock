package com.aiblockclock.domain.auth;

public record LoginSessionId(long value) {
    public static LoginSessionId of(long value) {
        return new LoginSessionId(value);
    }
}
