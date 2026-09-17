package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefreshTokenReader {
    private final RefreshTokenRepository refreshTokenRepository;

    public Optional<RefreshToken> findByRefreshTokenHash(String refreshTokenHash) {
        return refreshTokenRepository.findByRefreshTokenHash(refreshTokenHash)
                .map(RefreshTokenEntityConverter::toDomain);
    }
}
