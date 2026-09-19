package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LoginSessionPropertiesTest {

    @Test
    void 로그인_세션_시간은_1초_이상의_정수_초여야_한다() {
        assertThat(new LoginSessionProperties(Duration.ofDays(7)).isTtlValid()).isTrue();
        assertThat(new LoginSessionProperties(Duration.ofSeconds(1)).isTtlValid()).isTrue();
        assertThat(new LoginSessionProperties(Duration.ZERO).isTtlValid()).isFalse();
        assertThat(new LoginSessionProperties(Duration.ofSeconds(-1)).isTtlValid()).isFalse();
        assertThat(new LoginSessionProperties(Duration.ofMillis(1500)).isTtlValid()).isFalse();
    }

    @Test
    void 발급_결과를_문자열로_출력해도_토큰_원문은_노출하지_않는다() {
        IssuedSessionToken token = new IssuedSessionToken(
                "secret-session-token", java.time.Instant.parse("2026-09-24T00:00:00Z")
        );

        assertThat(token.toString()).contains("value=***").doesNotContain("secret-session-token");
    }
}
