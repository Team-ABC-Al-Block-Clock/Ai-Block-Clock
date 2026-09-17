package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.IssuedRefreshToken;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenCookieTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private final RefreshTokenCookie cookie = new RefreshTokenCookie(
            new AuthWebProperties(true, List.of("https://j15a403.p.ssafy.io")),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 배포_쿠키는_Secure_HttpOnly_경로_SameSite를_고정하고_남은_시간만_설정한다() {
        var created = cookie.create(new IssuedRefreshToken("a".repeat(64), NOW.plusSeconds(123)));
        assertThat(created.isSecure()).isTrue();
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.getPath()).isEqualTo("/api/v1/auth");
        assertThat(created.getSameSite()).isEqualTo("Lax");
        assertThat(created.getDomain()).isNull();
        assertThat(created.getMaxAge().getSeconds()).isEqualTo(123);
    }

    @Test
    void 쿠키_삭제도_발급과_같은_속성을_사용한다() {
        var cleared = cookie.clear();
        assertThat(cleared.getMaxAge().getSeconds()).isZero();
        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.isSecure()).isTrue();
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.getPath()).isEqualTo("/api/v1/auth");
    }

    @Test
    void 만료한_쿠키는_음수_수명으로_세션_쿠키가_되지_않는다() {
        assertThat(cookie.create(new IssuedRefreshToken("a".repeat(64), NOW.minusSeconds(1)))
                .getMaxAge().getSeconds()).isZero();
    }

    @Test
    void 누락과_잘못된_형식은_인증_실패로_처리한다() {
        assertThatThrownBy(() -> cookie.requireValue(null)).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_NOT_USABLE));
        assertThatThrownBy(() -> cookie.requireValue("A".repeat(64))).isInstanceOf(CoreException.class);
        assertThat(cookie.requireValue("a".repeat(64))).isEqualTo("a".repeat(64));
    }
}
