package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletAuthService {
    private final WalletAuthMessageGenerator walletAuthMessageGenerator;
    private final WalletAuthMessageReader walletAuthMessageReader;
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final WalletSignatureValidator walletSignatureValidator;
    private final UserReader userReader;
    private final WalletLoginManager walletLoginManager;
    private final Clock clock;

    public WalletAuthMessage issueMessage(String walletAddress) {
        WalletAuthMessage issuedMessage = walletAuthMessageGenerator.generate(walletAddress);

        return walletAuthMessageWriter.append(issuedMessage);
    }

    public User login(String walletAddress, String signature) {
        WalletAuthMessage authMessage = walletAuthMessageReader.findByWalletAddress(walletAddress)
                .orElseThrow(() -> new CoreException(ErrorType.WALLET_AUTH_MESSAGE_NOT_FOUND));
        authMessage.checkUsable(clock.instant());
        walletSignatureValidator.check(authMessage, signature);
        Optional<User> existingUser = userReader.findByWalletAddress(authMessage.walletAddress());

        if (existingUser.isPresent()) {
            walletAuthMessageWriter.modifyAsUsed(authMessage);

            return existingUser.get();
        }

        return walletLoginManager.register(authMessage);
    }
}
