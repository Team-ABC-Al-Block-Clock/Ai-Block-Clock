package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LoginSessionWriter {
    private final LoginSessionRepository loginSessionRepository;

    @Transactional
    public LoginSession append(UserId userId, Instant expiresAt) {
        LoginSessionEntity saved = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(userId.value(), expiresAt)
        );

        return LoginSessionEntityConverter.toDomain(saved);
    }
}
