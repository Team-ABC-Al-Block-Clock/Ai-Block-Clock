package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.RefreshTokenEntity;
import com.aiblockclock.storage.auth.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class RefreshTokenWriter {
    private final RefreshTokenRepository refreshTokenRepository;

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
}
