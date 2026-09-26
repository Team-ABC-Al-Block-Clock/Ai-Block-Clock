package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.identity.IdentityVerificationEntity;

import java.time.Instant;

final class IdentityVerificationEntityConverter {

    private IdentityVerificationEntityConverter() {
    }

    static IdentityVerification toDomain(IdentityVerificationEntity entity) {
        return IdentityVerification.restore(
                entity.getId(),
                UserId.of(entity.getUserId()),
                entity.getLegalName(),
                entity.getBirthDate(),
                entity.getStatus(),
                entity.getSubmittedAt(),
                entity.getReviewerId() == null ? null : UserId.of(entity.getReviewerId()),
                entity.getReviewedAt(),
                entity.getRejectReason()
        );
    }

    static IdentityVerificationEntity toNewEntity(IdentityVerification verification, Instant submittedAt) {
        return IdentityVerificationEntity.of(
                verification.getUserId().value(),
                verification.getLegalName(),
                verification.getBirthDate(),
                verification.getStatus(),
                submittedAt
        );
    }
}
