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
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenReader refreshTokenReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final AccessTokenGenerator accessTokenGenerator;
    private final AccessTokenValidator accessTokenValidator;
    private final LoginSessionReader loginSessionReader;
    private final LoginSessionWriter loginSessionWriter;
    private final AuthTokenProperties authTokenProperties;
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
        String rawRefreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        Instant sessionExpiresAt = now.plus(authTokenProperties.sessionTtl()).truncatedTo(ChronoUnit.MICROS);

        walletAuthMessageWriter.modifyAsUsed(authMessage);
        User user = userReader.findByWalletAddress(authMessage.walletAddress())
                .orElseGet(() -> userRegistrationManager.register(authMessage.walletAddress()));
        // 사용자 행 잠금으로 같은 사용자의 세션 교체를 순서대로 처리한다.
        userReader.findByIdForUpdate(user.id())
                .orElseThrow(() -> new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED));
        loginSessionWriter.modifyAllAsRevoked(user.id());
        LoginSession session = loginSessionWriter.append(user.id(), sessionExpiresAt);
        refreshTokenWriter.append(session.id(), refreshTokenHash, session.expiresAt());
        AccessToken accessToken = accessTokenGenerator.generate(session);

        return new WalletLogin(
                user,
                accessToken,
                new IssuedRefreshToken(rawRefreshToken, session.expiresAt())
        );
    }

    @Transactional
    public AuthTokens reissue(String rawRefreshToken) {
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenReader.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE));
        refreshToken.checkUsable(clock.instant());

        LoginSession session = loginSessionReader.findByIdForUpdate(refreshToken.sessionId())
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));
        // 잠금 대기 중 만료될 수 있으므로 잠금을 얻은 시각으로 검사한다.
        session.checkUsable(clock.instant());

        String newRawRefreshToken = refreshTokenGenerator.generate();
        String newRefreshTokenHash = refreshTokenHasher.hash(newRawRefreshToken);
        refreshTokenWriter.modifyAsUsed(refreshToken);
        refreshTokenWriter.append(session.id(), newRefreshTokenHash, session.expiresAt());
        AccessToken accessToken = accessTokenGenerator.generate(session);

        return new AuthTokens(
                accessToken,
                new IssuedRefreshToken(newRawRefreshToken, session.expiresAt())
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenReader.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE));
        refreshToken.checkUsable(clock.instant());

        loginSessionWriter.modifyAsRevoked(refreshToken.sessionId());
    }

    @Transactional(readOnly = true)
    public LoginSession authenticate(String rawAccessToken) {
        AccessTokenClaims claims = accessTokenValidator.readClaims(rawAccessToken);
        LoginSession session = loginSessionReader.findById(claims.sessionId())
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));

        session.checkUsable(clock.instant());

        if (!session.userId().equals(claims.userId())) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        return session;
    }
}
