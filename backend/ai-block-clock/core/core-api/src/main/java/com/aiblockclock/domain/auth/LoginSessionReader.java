package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.LoginSessionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginSessionReader {
    private final LoginSessionRepository loginSessionRepository;

    public Optional<LoginSession> findById(LoginSessionId id) {
        return loginSessionRepository.findById(id.value())
                .map(LoginSessionEntityConverter::toDomain);
    }

    public Optional<LoginSession> findBySessionTokenHash(String sessionTokenHash) {
        return loginSessionRepository.findBySessionTokenHash(sessionTokenHash)
                .map(LoginSessionEntityConverter::toDomain);
    }
}
