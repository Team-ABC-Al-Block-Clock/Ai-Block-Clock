package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;

final class LoginSessionEntityConverter {
    private LoginSessionEntityConverter() {
    }

    static LoginSession toDomain(LoginSessionEntity entity) {
        return new LoginSession(
                LoginSessionId.of(entity.getId()),
                UserId.of(entity.getUserId()),
                entity.getExpiresAt(),
                entity.getRevokedAt()
        );
    }
}
