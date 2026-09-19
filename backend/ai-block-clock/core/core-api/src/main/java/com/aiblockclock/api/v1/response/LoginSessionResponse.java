package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.auth.IssuedSessionToken;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record LoginSessionResponse(
        @Schema(description = "로그인 세션 만료 시각") Instant expiresAt
) {
    public static LoginSessionResponse from(IssuedSessionToken token) {
        return new LoginSessionResponse(token.expiresAt());
    }
}
