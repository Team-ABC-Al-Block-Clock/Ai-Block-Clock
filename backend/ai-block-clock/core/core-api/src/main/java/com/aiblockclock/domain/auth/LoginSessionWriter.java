package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class LoginSessionWriter {
    private final LoginSessionRepository loginSessionRepository;
    private final Clock clock;

    @Transactional
    public LoginSession append(UserId userId, Instant expiresAt) {
        LoginSessionEntity saved = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(userId.value(), expiresAt)
        );

        return LoginSessionEntityConverter.toDomain(saved);
    }

    @Transactional
    public void modifyAsRevoked(LoginSessionId sessionId) {
        Instant revokedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        loginSessionRepository.updateRevokedAtIfNotRevoked(sessionId.value(), revokedAt);
    }

    @Transactional
    public void modifyAllAsRevoked(UserId userId) {
        Instant revokedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        loginSessionRepository.updateRevokedAtByUserIdIfNotRevoked(userId.value(), revokedAt);
    }
}
