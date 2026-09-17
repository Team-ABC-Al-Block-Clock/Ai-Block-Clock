package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserRegistrationManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WalletLoginManager {
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final UserRegistrationManager userRegistrationManager;

    @Transactional
    public User register(WalletAuthMessage authMessage) {
        walletAuthMessageWriter.modifyAsUsed(authMessage);

        return userRegistrationManager.register(authMessage.walletAddress());
    }
}
