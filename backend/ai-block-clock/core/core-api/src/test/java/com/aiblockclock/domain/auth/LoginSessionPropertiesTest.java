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
}
