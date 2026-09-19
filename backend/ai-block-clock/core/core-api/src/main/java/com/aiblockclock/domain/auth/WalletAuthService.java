package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.domain.user.UserRegistrationManager;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class WalletAuthService {
    private final WalletAuthMessageGenerator walletAuthMessageGenerator;
    private final WalletAuthMessageReader walletAuthMessageReader;
    private final WalletAuthMessageWriter walletAuthMessageWriter;
    private final WalletSignatureValidator walletSignatureValidator;
    private final UserReader userReader;
    private final UserRegistrationManager userRegistrationManager;
    private final SessionTokenGenerator sessionTokenGenerator;
    private final SessionTokenHasher sessionTokenHasher;
    private final LoginSessionReader loginSessionReader;
    private final LoginSessionWriter loginSessionWriter;
    private final LoginSessionProperties loginSessionProperties;
    private final Clock clock;

    @Transactional
    public WalletAuthMessage issueMessage(String walletAddress) {
        WalletAuthMessage issuedMessage = walletAuthMessageGenerator.generate(walletAddress);

        return walletAuthMessageWriter.append(issuedMessage);
    }

    @Transactional
    public WalletLogin login(String walletAddress, String signature) {
        WalletAuthMessage authMessage = walletAuthMessageReader.findByWalletAddress(walletAddress)
                .orElseThrow(() -> new CoreException(ErrorType.WALLET_AUTH_MESSAGE_NOT_FOUND));
        Instant now = clock.instant();
        authMessage.checkUsable(now);
        walletSignatureValidator.check(authMessage, signature);

        // 원문은 응답으로만 내보내고 DB에는 해시만 저장한다.
        String rawSessionToken = sessionTokenGenerator.generate();
        String sessionTokenHash = sessionTokenHasher.hash(rawSessionToken);
        Instant sessionExpiresAt = now.plus(loginSessionProperties.ttl()).truncatedTo(ChronoUnit.MICROS);

        walletAuthMessageWriter.modifyAsUsed(authMessage);
        User user = userReader.findByWalletAddress(authMessage.walletAddress())
                .orElseGet(() -> userRegistrationManager.register(authMessage.walletAddress()));
        // 사용자 행 잠금으로 같은 사용자의 세션 교체를 순서대로 처리한다.
        userReader.findByIdForUpdate(user.id())
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED));
        loginSessionWriter.modifyAllAsRevoked(user.id());
        LoginSession session = loginSessionWriter.append(user.id(), sessionTokenHash, sessionExpiresAt);

        return new WalletLogin(
                user,
                new IssuedSessionToken(rawSessionToken, session.expiresAt())
        );
    }

    @Transactional
    public void logout(String rawSessionToken) {
        String sessionTokenHash = sessionTokenHasher.hash(rawSessionToken);
        LoginSession session = loginSessionReader.findBySessionTokenHash(sessionTokenHash)
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));
        session.checkUsable(clock.instant());

        loginSessionWriter.modifyAsRevoked(session.id());
    }

    @Transactional(readOnly = true)
    public LoginSession authenticate(String rawSessionToken) {
        String sessionTokenHash = sessionTokenHasher.hash(rawSessionToken);
        LoginSession session = loginSessionReader.findBySessionTokenHash(sessionTokenHash)
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));

        session.checkUsable(clock.instant());

        return session;
    }
}
