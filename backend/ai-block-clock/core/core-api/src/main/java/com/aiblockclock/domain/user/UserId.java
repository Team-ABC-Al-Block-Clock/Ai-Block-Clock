package com.aiblockclock.domain.user;

public record UserId(long value) {
    public static UserId of(long value) {
        return new UserId(value);
    }
}
