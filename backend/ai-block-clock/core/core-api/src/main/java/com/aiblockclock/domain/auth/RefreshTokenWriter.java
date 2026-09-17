package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.RefreshTokenEntity;
import com.aiblockclock.storage.auth.RefreshTokenRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class RefreshTokenWriter {
    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Transactional
    public RefreshToken append(
            LoginSessionId sessionId,
            String refreshTokenHash,
            Instant expiresAt
    ) {
        RefreshTokenEntity saved = refreshTokenRepository.saveAndFlush(
                RefreshTokenEntity.of(sessionId.value(), refreshTokenHash, expiresAt)
        );

        return RefreshTokenEntityConverter.toDomain(saved);
    }

    @Transactional
    public void modifyAsUsed(RefreshToken refreshToken) {
        Instant usedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        refreshToken.checkUsable(usedAt);

        int updatedCount = refreshTokenRepository.updateUsedAtIfUsable(
                refreshToken.id().value(), refreshToken.sessionId().value(), usedAt
        );

        if (updatedCount != 1) {
            throw new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE);
        }
    }
}
