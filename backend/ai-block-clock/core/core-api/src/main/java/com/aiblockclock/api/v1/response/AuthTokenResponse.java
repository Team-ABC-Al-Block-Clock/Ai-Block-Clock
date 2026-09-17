package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.auth.AccessToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;

public record AuthTokenResponse(
        @Schema(description = "API 요청의 Authorization 헤더에 보낼 Access Token") String accessToken,
        @Schema(description = "인증 방식", example = "Bearer") String tokenType,
        @Schema(description = "Access Token 만료까지 남은 초", example = "900") long expiresIn
) {

    public static AuthTokenResponse from(AccessToken token, Instant now) {
        long remainingSeconds = Math.max(0, Duration.between(now, token.expiresAt()).getSeconds());
        return new AuthTokenResponse(token.value(), "Bearer", remainingSeconds);
    }

    @Override
    public String toString() {
        return "AuthTokenResponse[accessToken=***, tokenType=%s, expiresIn=%d]"
                .formatted(tokenType, expiresIn);
    }
}
