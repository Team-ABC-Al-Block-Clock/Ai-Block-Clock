package com.aiblockclock.domain;

public record OwnerId(long value) {
    public static OwnerId of(long value) {
        return new OwnerId(value);
    }

}
