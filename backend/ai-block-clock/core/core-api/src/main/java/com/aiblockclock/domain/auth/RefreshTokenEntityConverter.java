package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.RefreshTokenEntity;

final class RefreshTokenEntityConverter {
    private RefreshTokenEntityConverter() {
    }

    static RefreshToken toDomain(RefreshTokenEntity entity) {
        return new RefreshToken(
                RefreshTokenId.of(entity.getId()),
                LoginSessionId.of(entity.getSessionId()),
                entity.getRefreshTokenHash(),
                entity.getExpiresAt(),
                entity.getUsedAt()
        );
    }
}
