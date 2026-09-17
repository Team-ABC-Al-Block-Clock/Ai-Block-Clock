package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.time.Clock;
import java.util.Base64;
import java.util.Date;

@Component
public class AccessTokenValidator {
    private final Clock clock;
    private final JwtParser parser;

    public AccessTokenValidator(AuthTokenProperties properties, Clock clock) {
        this.clock = clock;
        SecretKey signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secretKeyBase64()));

        this.parser = Jwts.parser()
                .verifyWith(signingKey)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public AccessTokenClaims readClaims(String rawAccessToken) {
        try {
            Claims claims = parser.parseSignedClaims(rawAccessToken).getPayload();
            checkExpiration(claims);

            UserId userId = UserId.of(readPositiveId(claims.get("sub")));
            LoginSessionId sessionId = LoginSessionId.of(readPositiveId(claims.get("sid")));

            return new AccessTokenClaims(userId, sessionId);
        } catch (ExpiredJwtException exception) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException exception) {
            // JWT 예외의 메시지나 원문을 응답·로그에 포함하지 않는다.
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }
    }

    private void checkExpiration(Claims claims) {
        Date expiration = claims.getExpiration();

        if (expiration == null) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        if (!clock.instant().isBefore(expiration.toInstant())) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_EXPIRED);
        }
    }

    private long readPositiveId(Object claim) {
        if (!(claim instanceof String value) || !value.matches("[1-9][0-9]*")) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        return Long.parseLong(value);
    }
}
