package com.aiblockclock.domain.node;

import java.util.UUID;

public record RefFileId(UUID value) {
    public static RefFileId of(UUID value) {
        return new RefFileId(value);
    }

    public static RefFileId of(String value) {
        return new RefFileId(UUID.fromString(value));
    }

    public static RefFileId newId() {
        return new RefFileId(UUID.randomUUID());
    }
}
