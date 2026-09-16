package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class UserWriter {
    private final UserRepository userRepository;

    @Transactional
    public User create(String walletAddress) {
        String normalizedWalletAddress = walletAddress.toLowerCase(Locale.ROOT);

        UserEntity saved = userRepository.saveAndFlush(
                UserEntity.of(normalizedWalletAddress)
        );

        return UserEntityConverter.toDomain(saved);
    }
}
