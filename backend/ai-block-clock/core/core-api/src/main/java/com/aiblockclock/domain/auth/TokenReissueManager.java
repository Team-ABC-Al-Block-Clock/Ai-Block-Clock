package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class TokenReissueManager {
    private final LoginSessionReader loginSessionReader;
    private final RefreshTokenWriter refreshTokenWriter;
    private final AccessTokenGenerator accessTokenGenerator;
    private final Clock clock;

    @Transactional
    public AccessToken reissue(RefreshToken refreshToken, String newRefreshTokenHash) {
        LoginSession session = loginSessionReader.findByIdForUpdate(refreshToken.sessionId())
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));
        session.checkUsable(clock.instant());

        refreshTokenWriter.modifyAsUsed(refreshToken);
        refreshTokenWriter.append(session.id(), newRefreshTokenHash, session.expiresAt());

        return accessTokenGenerator.generate(session);
    }
}
