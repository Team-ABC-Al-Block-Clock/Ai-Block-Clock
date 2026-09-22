package com.aiblockclock.domain;

import java.time.Instant;

public record Timestamps(Instant createdAt, Instant updatedAt) {
    private static final Timestamps UNSAVED = new Timestamps(null, null);

    public static Timestamps unsaved() {
        return UNSAVED;
    }
}
