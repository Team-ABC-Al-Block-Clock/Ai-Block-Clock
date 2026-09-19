package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenHasherTest {
    private final SessionTokenHasher hasher = new SessionTokenHasher();

    @Test
    void 같은_원문은_항상_같은_SHA_256_해시가_되고_원문은_남지_않는다() {
        String rawToken = "a".repeat(64);

        String hash = hasher.hash(rawToken);

        assertThat(hash).isEqualTo(hasher.hash(rawToken));
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(hash).isNotEqualTo(rawToken);
        assertThat(hasher.hash("b".repeat(64))).isNotEqualTo(hash);
    }
}
