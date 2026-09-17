package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenGeneratorTest {
    private static final String TEST_SECRET_KEY_BASE64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456789Z");
    private static final Instant ISSUED_AT = NOW.truncatedTo(ChronoUnit.SECONDS);
    private static final Instant SESSION_EXPIRES_AT = NOW.plus(Duration.ofDays(7))
            .truncatedTo(ChronoUnit.MICROS);

    private final AuthTokenProperties properties = new AuthTokenProperties(
            "ai-block-clock", Duration.ofMinutes(15), Duration.ofDays(7), TEST_SECRET_KEY_BASE64
    );
    private final SecretKey signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET_KEY_BASE64));
    private final AccessTokenGenerator generator = new AccessTokenGenerator(
            properties, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void 사용자와_세션_번호를_담고_15분_만료의_HS256_JWT를_생성한다() throws Exception {
        AccessToken token = generator.generate(session(SESSION_EXPIRES_AT, null));

        Jws<Claims> parsed = parse(token, NOW, signingKey);
        Claims claims = parsed.getPayload();
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(claims).containsOnlyKeys("iss", "sub", "sid", "iat", "exp");
        assertThat(claims.getIssuer()).isEqualTo(properties.issuer());
        assertThat(claims.getSubject()).isEqualTo("10");
        assertThat(claims.get("sid", String.class)).isEqualTo("101");
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(ISSUED_AT);
        assertThat(claims.getExpiration().toInstant()).isEqualTo(ISSUED_AT.plusSeconds(900));
        assertThat(token.expiresAt()).isEqualTo(claims.getExpiration().toInstant());

        String[] parts = token.value().split("\\.");
        assertThat(parts).hasSize(3);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(signingKey);
        byte[] expectedSignature = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(expectedSignature);
    }

    @Test
    void 세션_만료가_더_빠르면_Access_Token도_그_시각까지로_제한한다() {
        Instant sessionExpiresAt = NOW.plusSeconds(60);

        AccessToken token = generator.generate(session(sessionExpiresAt, null));

        assertThat(token.expiresAt()).isEqualTo(sessionExpiresAt.truncatedTo(ChronoUnit.SECONDS));
        assertThat(token.expiresAt()).isBeforeOrEqualTo(sessionExpiresAt);
        assertThat(parse(token, NOW, signingKey).getPayload().getExpiration().toInstant())
                .isEqualTo(token.expiresAt());
    }

    @Test
    void 설정된_Access_Token_유효_시간을_사용한다() {
        AuthTokenProperties customProperties = new AuthTokenProperties(
                "ai-block-clock", Duration.ofMinutes(5), Duration.ofDays(7), TEST_SECRET_KEY_BASE64
        );
        AccessTokenGenerator customGenerator = new AccessTokenGenerator(
                customProperties, Clock.fixed(NOW, ZoneOffset.UTC)
        );

        AccessToken token = customGenerator.generate(session(SESSION_EXPIRES_AT, null));

        assertThat(token.expiresAt()).isEqualTo(ISSUED_AT.plusSeconds(300));
    }

    @Test
    void 같은_세션으로_나중에_발급해도_기존_세션_만료_시각은_연장하지_않는다() {
        Instant later = SESSION_EXPIRES_AT.minusSeconds(30);
        AccessTokenGenerator laterGenerator = new AccessTokenGenerator(
                properties, Clock.fixed(later, ZoneOffset.UTC)
        );

        AccessToken token = laterGenerator.generate(session(SESSION_EXPIRES_AT, null));

        assertThat(token.expiresAt()).isEqualTo(SESSION_EXPIRES_AT.truncatedTo(ChronoUnit.SECONDS));
        assertThat(parse(token, later, signingKey).getPayload().getExpiration().toInstant())
                .isEqualTo(token.expiresAt());
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L})
    void 만료_시각에_도달하거나_지난_세션은_토큰을_발급하지_않는다(long secondsAfterExpiry) {
        assertThatThrownBy(() -> generator.generate(session(NOW.minusSeconds(secondsAfterExpiry), null)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_EXPIRED));
    }

    @Test
    void 종료된_세션은_토큰을_발급하지_않는다() {
        assertThatThrownBy(() -> generator.generate(session(SESSION_EXPIRES_AT, NOW.minusSeconds(30))))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
    }

    @Test
    void 초_단위로_표현하면_이미_만료될_세션은_토큰을_발급하지_않는다() {
        Instant sessionExpiresAt = ISSUED_AT.plusNanos(999_999_999);

        assertThatThrownBy(() -> generator.generate(session(sessionExpiresAt, null)))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_NOT_USABLE));
    }

    @Test
    void 사용자_번호를_변조하면_서명_검증에_실패한다() {
        AccessToken token = generator.generate(session(SESSION_EXPIRES_AT, null));
        String[] parts = token.value().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("\"sub\":\"10\"");
        String tamperedPayload = payload.replace("\"sub\":\"10\"", "\"sub\":\"20\"");
        String tamperedValue = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> parse(new AccessToken(tamperedValue, token.expiresAt()), NOW, signingKey))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 다른_비밀키로는_발급한_토큰을_검증할_수_없다() {
        AccessToken token = generator.generate(session(SESSION_EXPIRES_AT, null));
        SecretKey anotherKey = Jwts.SIG.HS256.key().build();

        assertThatThrownBy(() -> parse(token, NOW, anotherKey))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void 발급한_토큰은_만료_시각이_지나면_검증에서_거절된다() {
        AccessToken token = generator.generate(session(SESSION_EXPIRES_AT, null));

        assertThatThrownBy(() -> parse(token, token.expiresAt().plusSeconds(1), signingKey))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 문자열_표시에_서버_비밀키와_토큰_원문을_노출하지_않는다() {
        AccessToken token = generator.generate(session(SESSION_EXPIRES_AT, null));

        assertThat(properties.toString()).doesNotContain(TEST_SECRET_KEY_BASE64);
        assertThat(token.toString()).doesNotContain(token.value());
    }

    private Jws<Claims> parse(AccessToken token, Instant now, SecretKey key) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .clock(() -> Date.from(now))
                .build()
                .parseSignedClaims(token.value());
    }

    private LoginSession session(Instant expiresAt, Instant revokedAt) {
        return new LoginSession(LoginSessionId.of(101L), UserId.of(10L), expiresAt, revokedAt);
    }
}
