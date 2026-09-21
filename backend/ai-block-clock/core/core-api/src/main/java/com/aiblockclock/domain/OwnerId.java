package com.aiblockclock.domain;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

public record OwnerId(long value) implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static OwnerId of(long value) {
        return new OwnerId(value);
    }

    @Override
    public String getName() {
        return Long.toString(value);
    }
}
