package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

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
    private final WalletLoginManager walletLoginManager;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final RefreshTokenReader refreshTokenReader;
    private final TokenReissueManager tokenReissueManager;
    private final AccessTokenValidator accessTokenValidator;
    private final LoginSessionReader loginSessionReader;
    private final AuthTokenProperties authTokenProperties;
    private final Clock clock;

    public WalletAuthMessage issueMessage(String walletAddress) {
        WalletAuthMessage issuedMessage = walletAuthMessageGenerator.generate(walletAddress);

        return walletAuthMessageWriter.append(issuedMessage);
    }

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

        WalletLoginSession loginSession = userReader.findByWalletAddress(authMessage.walletAddress())
                .map(user -> walletLoginManager.login(authMessage, user, refreshTokenHash, sessionExpiresAt))
                .orElseGet(() -> walletLoginManager.register(authMessage, refreshTokenHash, sessionExpiresAt));

        return new WalletLogin(
                loginSession.user(),
                loginSession.accessToken(),
                new IssuedRefreshToken(rawRefreshToken, loginSession.session().expiresAt())
        );
    }

    public AuthTokens reissue(String rawRefreshToken) {
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshToken refreshToken = refreshTokenReader.findByRefreshTokenHash(refreshTokenHash)
                .orElseThrow(() -> new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE));
        Instant now = clock.instant();
        refreshToken.checkUsable(now);

        LoginSession session = loginSessionReader.findById(refreshToken.sessionId())
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));
        session.checkUsable(now);

        String newRawRefreshToken = refreshTokenGenerator.generate();
        String newRefreshTokenHash = refreshTokenHasher.hash(newRawRefreshToken);
        AccessToken accessToken = tokenReissueManager.reissue(refreshToken, newRefreshTokenHash);

        return new AuthTokens(
                accessToken,
                new IssuedRefreshToken(newRawRefreshToken, session.expiresAt())
        );
    }

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
