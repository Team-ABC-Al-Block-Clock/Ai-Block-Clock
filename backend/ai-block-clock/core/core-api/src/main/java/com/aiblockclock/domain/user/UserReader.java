package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReader {
    private final UserRepository userRepository;

    public Optional<User> findByWalletAddress(String walletAddress) {
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(UserEntityConverter::toDomain);
    }
}
