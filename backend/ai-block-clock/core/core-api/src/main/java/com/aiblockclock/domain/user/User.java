package com.aiblockclock.domain.user;

import java.time.Instant;

public record User(
        UserId id,
        String walletAddress,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
