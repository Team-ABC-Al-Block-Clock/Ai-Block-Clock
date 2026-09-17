package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserRegistrationManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class WalletLoginManager {
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final UserRegistrationManager userRegistrationManager;
    private final LoginSessionManager loginSessionManager;
    private final AccessTokenGenerator accessTokenGenerator;

    @Transactional
    public WalletLoginSession login(
            WalletAuthMessage authMessage,
            User user,
            String refreshTokenHash,
            Instant sessionExpiresAt
    ) {
        walletAuthMessageWriter.modifyAsUsed(authMessage);

        return openSession(user, refreshTokenHash, sessionExpiresAt);
    }

    @Transactional
    public WalletLoginSession register(
            WalletAuthMessage authMessage,
            String refreshTokenHash,
            Instant sessionExpiresAt
    ) {
        walletAuthMessageWriter.modifyAsUsed(authMessage);

        User user = userRegistrationManager.register(authMessage.walletAddress());

        return openSession(user, refreshTokenHash, sessionExpiresAt);
    }

    private WalletLoginSession openSession(
            User user,
            String refreshTokenHash,
            Instant sessionExpiresAt
    ) {
        LoginSession session = loginSessionManager.create(user.id(), refreshTokenHash, sessionExpiresAt);
        AccessToken accessToken = accessTokenGenerator.generate(session);

        return new WalletLoginSession(user, session, accessToken);
    }
}
