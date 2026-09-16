package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;

final class UserEntityConverter {
    private UserEntityConverter() {
    }

    static User toDomain(UserEntity entity) {
        return new User(
                UserId.of(entity.getId()),
                entity.getWalletAddress(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
