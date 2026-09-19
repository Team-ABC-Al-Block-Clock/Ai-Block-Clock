package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTokenGeneratorTest {
    private final SessionTokenGenerator generator = new SessionTokenGenerator();

    @Test
    void 예측하기_어려운_64자리_소문자_16진수_토큰을_만든다() {
        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).matches("[0-9a-f]{64}");
        assertThat(second).matches("[0-9a-f]{64}");
        assertThat(second).isNotEqualTo(first);
    }
}
