package com.aiblockclock.domain.user;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;

public record UserId(long value) implements Principal, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public static UserId of(long value) {
        return new UserId(value);
    }

    /** Spring Session 이 세션을 사용자별로 찾을 때 쓰는 이름이다. */
    @Override
    public String getName() {
        return Long.toString(value);
    }
}
