package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void 만료_직전까지_미사용_토큰은_사용할_수_있다() {
        assertThatCode(() -> token(null).checkUsable(EXPIRES_AT.minusNanos(1)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 60L})
    void 만료_시각에_도달하거나_지나면_거절한다(long secondsAfterExpiry) {
        assertThatThrownBy(() -> token(null).checkUsable(EXPIRES_AT.plusSeconds(secondsAfterExpiry)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_EXPIRED));
    }

    @Test
    void 만료되지_않았어도_이미_사용한_토큰은_거절한다() {
        Instant usedAt = EXPIRES_AT.minusSeconds(60);

        assertThatThrownBy(() -> token(usedAt).checkUsable(EXPIRES_AT.minusSeconds(30)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_ALREADY_USED));
    }

    private RefreshToken token(Instant usedAt) {
        return new RefreshToken(RefreshTokenId.of(500L), LoginSessionId.of(100L),
                "a".repeat(64), EXPIRES_AT, usedAt);
    }
}
