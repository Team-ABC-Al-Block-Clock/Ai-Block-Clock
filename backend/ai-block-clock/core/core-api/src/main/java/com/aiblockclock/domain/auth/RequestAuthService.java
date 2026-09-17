package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class RequestAuthService {
    private final AccessTokenValidator accessTokenValidator;
    private final LoginSessionReader loginSessionReader;
    private final Clock clock;

    public LoginSession authenticate(String rawAccessToken) {
        AccessTokenClaims claims = accessTokenValidator.readClaims(rawAccessToken);
        LoginSession session = loginSessionReader.findById(claims.sessionId())
                .orElseThrow(() -> new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE));

        session.checkUsable(clock.instant());

        // 토큰이 말하는 사용자와 세션에 저장된 사용자가 어긋나면 토큰을 신뢰할 수 없다.
        if (!session.userId().equals(claims.userId())) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        return session;
    }
}
