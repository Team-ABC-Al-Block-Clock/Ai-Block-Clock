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
import java.util.Arrays;
import java.util.Optional;

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

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return Optional.empty();
        }

        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    public String readRequired(HttpServletRequest request) {
        return read(request)
                .map(this::requireValue)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED));
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
