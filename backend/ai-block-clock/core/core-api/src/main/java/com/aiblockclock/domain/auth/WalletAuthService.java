package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.domain.user.UserRegistrationManager;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletAuthService {
    private final WalletAuthMessageGenerator walletAuthMessageGenerator;
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final WalletAuthenticator walletAuthenticator;
    private final UserReader userReader;
    private final UserRegistrationManager userRegistrationManager;

    @Transactional
    public WalletAuthMessage issueMessage(String walletAddress) {
        WalletAuthMessage issuedMessage = walletAuthMessageGenerator.generate(walletAddress);

        return walletAuthMessageWriter.append(issuedMessage);
    }

    @Transactional
    public User login(String walletAddress, String signature) {
        String verifiedWalletAddress = walletAuthenticator.authenticate(walletAddress, signature);

        return userReader.findByWalletAddress(verifiedWalletAddress)
                .orElseGet(() -> userRegistrationManager.register(verifiedWalletAddress));
    }
}
