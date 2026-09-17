package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;

public record AccessTokenClaims(UserId userId, LoginSessionId sessionId) {
}
