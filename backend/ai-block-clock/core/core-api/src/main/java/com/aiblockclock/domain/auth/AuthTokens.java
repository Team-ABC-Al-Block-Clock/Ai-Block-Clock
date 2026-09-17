package com.aiblockclock.domain.auth;

public record AuthTokens(
        AccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
