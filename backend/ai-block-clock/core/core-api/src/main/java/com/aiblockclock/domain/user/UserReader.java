package com.aiblockclock.domain.user;

import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserReader {
    private final UserRepository userRepository;

    // 세션은 남았는데 사용자 행이 없으면(DB 초기화 뒤 남은 쿠키) 다시 로그인하게 401 로 돌려보낸다.
    public User read(UserId userId) {
        return userRepository.findById(userId.value())
                .map(UserEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE));
    }

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
