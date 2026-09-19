package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.IssuedSessionToken;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class SessionTokenCookie {
    public static final String NAME = "sessionToken";
    private static final String PATH = "/api/v1";
    private static final String TOKEN_PATTERN = "[0-9a-f]{64}";

    private final AuthWebProperties properties;
    private final Clock clock;

    public ResponseCookie create(IssuedSessionToken token) {
        long remainingSeconds = Math.max(0, Duration.between(clock.instant(), token.expiresAt()).getSeconds());

        return builder(token.value()).maxAge(remainingSeconds).build();
    }

    public ResponseCookie clear() {
        return builder("").maxAge(0).build();
    }

    public String readRequired(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME.equals(cookie.getName())) {
                    return requireValue(cookie.getValue());
                }
            }
        }

        throw new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED);
    }

    public String requireValue(String value) {
        if (value == null || !value.matches(TOKEN_PATTERN)) {
            throw new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE);
        }

        return value;
    }

    private ResponseCookie.ResponseCookieBuilder builder(String value) {
        return ResponseCookie.from(NAME, value)
                .path(PATH)
                .httpOnly(true)
                .secure(properties.secureCookie())
                .sameSite("Lax");
    }
}
