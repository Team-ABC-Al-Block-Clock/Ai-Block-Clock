package com.aiblockclock.domain.user;

import java.time.Instant;

public record User(
        UserId id,
        String walletAddress,
        String nickname,
        String introduction,
        Instant onboardingCompletedAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean onboardingCompleted() {
        return onboardingCompletedAt != null;
    }
}
