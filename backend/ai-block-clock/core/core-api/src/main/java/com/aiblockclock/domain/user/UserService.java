package com.aiblockclock.domain.user;

import com.aiblockclock.domain.auth.WalletAuthProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserReader userReader;

    private final UserWriter userWriter;

    private final WalletAuthProperties walletAuthProperties;

    // 사용자가 로그인한 네트워크는 서명 검증에 쓴 설정과 같다.
    public UserProfile getProfile(UserId userId) {
        return new UserProfile(userReader.read(userId), walletAuthProperties.network());
    }

    @Transactional
    public UserProfile signup(UserId userId, UserSignup signup) {
        return new UserProfile(userWriter.completeOnboarding(userId, signup), walletAuthProperties.network());
    }
}
