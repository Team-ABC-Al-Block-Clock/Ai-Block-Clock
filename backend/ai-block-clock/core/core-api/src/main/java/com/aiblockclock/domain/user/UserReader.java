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

    // 세션 변경 Manager의 쓰기 트랜잭션에 참여하여 사용자별 처리를 직렬화한다.
    @Transactional
    public Optional<User> findByIdForUpdate(UserId userId) {
        return userRepository.findByIdForUpdate(userId.value())
                .map(UserEntityConverter::toDomain);
    }
}
