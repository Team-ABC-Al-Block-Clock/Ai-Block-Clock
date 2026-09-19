package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.IssuedSessionToken;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTokenCookieTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");
    private final SessionTokenCookie cookie = new SessionTokenCookie(
            new AuthWebProperties(true, List.of("https://j15a403.p.ssafy.io")),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void 쿠키는_전체_API에_전송되며_자바스크립트에서_읽을_수_없다() {
        var created = cookie.create(new IssuedSessionToken("a".repeat(64), NOW.plusSeconds(123)));

        assertThat(created.getName()).isEqualTo(SessionTokenCookie.NAME);
        assertThat(created.getPath()).isEqualTo("/api/v1");
        assertThat(created.isHttpOnly()).isTrue();
        assertThat(created.isSecure()).isTrue();
        assertThat(created.getSameSite()).isEqualTo("Lax");
        assertThat(created.getMaxAge().getSeconds()).isEqualTo(123);
        assertThat(created.getDomain()).isNull();
    }

    @Test
    void 삭제_쿠키도_같은_보안_속성과_경로를_사용한다() {
        var cleared = cookie.clear();

        assertThat(cleared.getValue()).isEmpty();
        assertThat(cleared.getMaxAge().getSeconds()).isZero();
        assertThat(cleared.getPath()).isEqualTo("/api/v1");
        assertThat(cleared.isHttpOnly()).isTrue();
        assertThat(cleared.isSecure()).isTrue();
    }

    @Test
    void 요청에서_정상_토큰만_읽고_누락이나_잘못된_형식은_거절한다() {
        MockHttpServletRequest valid = new MockHttpServletRequest();
        valid.setCookies(new Cookie(SessionTokenCookie.NAME, "a".repeat(64)));
        assertThat(cookie.readRequired(valid)).isEqualTo("a".repeat(64));

        assertThatThrownBy(() -> cookie.readRequired(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REQUESTER_NOT_IDENTIFIED));
        assertThatThrownBy(() -> cookie.requireValue("A".repeat(64)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_NOT_USABLE));
    }
}
