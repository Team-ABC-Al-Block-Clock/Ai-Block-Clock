package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.IssuedRefreshToken;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookie {
    public static final String NAME = "refreshToken";
    private static final String PATH = "/api/v1/auth";

    private final AuthWebProperties properties;
    private final Clock clock;

    public ResponseCookie create(IssuedRefreshToken token) {
        long remainingSeconds = Math.max(0, Duration.between(clock.instant(), token.expiresAt()).getSeconds());
        return builder(token.value()).maxAge(remainingSeconds).build();
    }

    public ResponseCookie clear() {
        return builder("").maxAge(0).build();
    }

    public String requireValue(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE);
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
