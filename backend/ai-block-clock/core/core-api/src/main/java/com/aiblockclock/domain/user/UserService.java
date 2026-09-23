package com.aiblockclock.domain.user;

import com.aiblockclock.domain.auth.WalletAuthProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserReader userReader;

    private final WalletAuthProperties walletAuthProperties;

    // 사용자가 로그인한 네트워크는 서명 검증에 쓴 설정과 같다.
    public UserProfile getProfile(UserId userId) {
        return new UserProfile(userReader.read(userId), walletAuthProperties.network());
    }
}
