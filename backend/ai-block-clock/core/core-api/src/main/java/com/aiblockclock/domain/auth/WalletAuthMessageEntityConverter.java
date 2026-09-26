package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;

final class WalletAuthMessageEntityConverter {
    private WalletAuthMessageEntityConverter() {
    }

    static WalletAuthMessage toDomain(WalletAuthMessageEntity entity) {
        return new WalletAuthMessage(
                entity.getWalletAddress(),
                entity.getNonce(),
                entity.getMessage(),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getUsedAt()
        );
    }
}
