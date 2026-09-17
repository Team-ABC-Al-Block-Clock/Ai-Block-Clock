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

    // 재발급 Manager의 쓰기 트랜잭션에서 종료 처리와 겹치지 않도록 세션을 잠근다.
    @Transactional
    public Optional<LoginSession> findByIdForUpdate(LoginSessionId id) {
        return loginSessionRepository.findByIdForUpdate(id.value())
                .map(LoginSessionEntityConverter::toDomain);
    }
}
