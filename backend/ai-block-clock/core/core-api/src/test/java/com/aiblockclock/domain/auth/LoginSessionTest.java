package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginSessionTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T00:00:00Z");

    @Test
    void 만료_직전까지_종료되지_않은_세션은_사용할_수_있다() {
        assertThatCode(() -> session(null).checkUsable(EXPIRES_AT.minusNanos(1)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 60L})
    void 만료_시각에_도달하거나_지나면_거절한다(long secondsAfterExpiry) {
        assertThatThrownBy(() -> session(null).checkUsable(EXPIRES_AT.plusSeconds(secondsAfterExpiry)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_EXPIRED));
    }

    @Test
    void 만료되지_않았어도_종료된_세션은_거절한다() {
        Instant revokedAt = EXPIRES_AT.minusSeconds(60);

        assertThatThrownBy(() -> session(revokedAt).checkUsable(EXPIRES_AT.minusSeconds(30)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
    }

    private LoginSession session(Instant revokedAt) {
        return new LoginSession(LoginSessionId.of(100L), UserId.of(10L), EXPIRES_AT, revokedAt);
    }
}
