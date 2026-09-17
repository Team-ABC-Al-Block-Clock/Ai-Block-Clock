package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Component
public class AccessTokenGenerator {
    private final AuthTokenProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    public AccessTokenGenerator(AuthTokenProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretKeyBase64()));
    }

    public AccessToken generate(LoginSession session) {
        Instant now = clock.instant();
        session.checkUsable(now);

        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant configuredExpiresAt = issuedAt.plus(properties.accessTokenTtl());
        Instant expiresAt = (configuredExpiresAt.isBefore(session.expiresAt())
                ? configuredExpiresAt : session.expiresAt()).truncatedTo(ChronoUnit.SECONDS);

        // JWT의 초 단위 만료 시각으로 이미 만료된 토큰이 만들어지는 것을 막는다.
        if (!now.isBefore(expiresAt)) {
            throw new CoreException(ErrorType.LOGIN_SESSION_NOT_USABLE);
        }

        String value = Jwts.builder()
                .issuer(properties.issuer())
                .subject(Long.toString(session.userId().value()))
                .claim("sid", Long.toString(session.id().value()))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();

        return new AccessToken(value, expiresAt);
    }
}
