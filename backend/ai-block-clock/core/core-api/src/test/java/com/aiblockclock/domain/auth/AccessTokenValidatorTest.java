package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenValidatorTest {
    private static final String TEST_SECRET_KEY_BASE64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456789Z");
    private static final Instant EXPIRES_AT = NOW.truncatedTo(ChronoUnit.SECONDS).plusSeconds(900);
    private static final String CLAIMS_JSON = """
            {"iss":"ai-block-clock","sub":"10","sid":"101","exp":%d}
            """.formatted(EXPIRES_AT.getEpochSecond()).strip();

    private final AuthTokenProperties properties = new AuthTokenProperties(
            "ai-block-clock", Duration.ofMinutes(15), Duration.ofDays(7), TEST_SECRET_KEY_BASE64
    );
    private final SecretKey signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET_KEY_BASE64));
    private final AccessTokenValidator validator = validatorAt(NOW);

    @Test
    void 생성기가_발급한_토큰에서_사용자와_세션_번호를_반환한다() {
        AccessTokenGenerator generator = new AccessTokenGenerator(properties, Clock.fixed(NOW, ZoneOffset.UTC));
        LoginSession session = new LoginSession(
                LoginSessionId.of(101L), UserId.of(10L), NOW.plus(Duration.ofDays(7)), null
        );
        AccessToken token = generator.generate(session);

        AccessTokenClaims claims = validator.readClaims(token.value());

        assertThat(claims.userId()).isEqualTo(UserId.of(10L));
        assertThat(claims.sessionId()).isEqualTo(LoginSessionId.of(101L));
    }

    @Test
    void 만료_직전에는_검증을_통과한다() {
        AccessTokenClaims claims = validatorAt(EXPIRES_AT.minusNanos(1)).readClaims(sign(CLAIMS_JSON));

        assertThat(claims.userId().value()).isEqualTo(10L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 1_000_000_000L})
    void 만료_시각과_같거나_이후이면_거절한다(long nanosAfterExpiry) {
        assertRejected(validatorAt(EXPIRES_AT.plusNanos(nanosAfterExpiry)), sign(CLAIMS_JSON),
                ErrorType.ACCESS_TOKEN_EXPIRED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "invalid", "a.b.c", "Bearer a.b.c"})
    void 토큰_원문이_없거나_형식이_잘못되면_거절한다(String rawAccessToken) {
        assertRejected(validator, rawAccessToken, ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 사용자_번호가_변조된_토큰은_거절한다() {
        String[] parts = sign(CLAIMS_JSON).split("\\.");
        String tamperedPayload = CLAIMS_JSON.replace("\"sub\":\"10\"", "\"sub\":\"20\"");
        String tamperedToken = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tamperedPayload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertRejected(validator, tamperedToken, ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 다른_비밀키로_서명한_토큰은_거절한다() {
        SecretKey anotherKey = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder().content(CLAIMS_JSON.getBytes(StandardCharsets.UTF_8))
                .signWith(anotherKey, Jwts.SIG.HS256).compact();

        assertRejected(validator, token, ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 같은_비밀키라도_HS384_알고리즘이면_거절한다() {
        SecretKey longKey = Jwts.SIG.HS512.key().build();
        AuthTokenProperties longKeyProperties = new AuthTokenProperties(
                properties.issuer(), properties.accessTokenTtl(), properties.sessionTtl(),
                Base64.getEncoder().encodeToString(longKey.getEncoded())
        );
        AccessTokenValidator longKeyValidator = new AccessTokenValidator(
                longKeyProperties, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        String content = CLAIMS_JSON;
        String allowedToken = Jwts.builder().content(content.getBytes(StandardCharsets.UTF_8))
                .signWith(longKey, Jwts.SIG.HS256).compact();
        String forbiddenToken = Jwts.builder().content(content.getBytes(StandardCharsets.UTF_8))
                .signWith(longKey, Jwts.SIG.HS384).compact();

        assertThat(longKeyValidator.readClaims(allowedToken).userId().value()).isEqualTo(10L);
        assertRejected(longKeyValidator, forbiddenToken, ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 서명_없는_토큰은_거절한다() {
        String token = Jwts.builder().issuer(properties.issuer()).subject("10")
                .claim("sid", "101").expiration(Date.from(EXPIRES_AT)).compact();

        assertRejected(validator, token, ErrorType.ACCESS_TOKEN_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"another-service\"", "null", "10"})
    void 발급자가_다르거나_없거나_문자열이_아니면_거절한다(String issuerJson) {
        String content = CLAIMS_JSON.replace("\"iss\":\"ai-block-clock\"", "\"iss\":" + issuerJson);

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 발급자_필드가_없으면_거절한다() {
        String content = CLAIMS_JSON.replace("\"iss\":\"ai-block-clock\",", "");

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 만료_시각이_없으면_거절한다() {
        String content = CLAIMS_JSON.replace(",\"exp\":" + EXPIRES_AT.getEpochSecond(), "");

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 만료_시각을_해석할_수_없으면_거절한다() {
        String content = CLAIMS_JSON.replace("\"exp\":" + EXPIRES_AT.getEpochSecond(), "\"exp\":\"invalid\"");

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @ParameterizedTest
    @MethodSource("invalidIdJsonValues")
    void 사용자_번호의_타입이나_형식이나_범위가_잘못되면_거절한다(String idJson) {
        String content = CLAIMS_JSON.replace("\"sub\":\"10\"", "\"sub\":" + idJson);

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @ParameterizedTest
    @MethodSource("invalidIdJsonValues")
    void 세션_번호의_타입이나_형식이나_범위가_잘못되면_거절한다(String idJson) {
        String content = CLAIMS_JSON.replace("\"sid\":\"101\"", "\"sid\":" + idJson);

        assertRejected(validator, sign(content), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sub", "sid"})
    void 사용자나_세션_번호_필드가_없으면_거절한다(String claimName) {
        String entry = claimName.equals("sub") ? "\"sub\":\"10\"," : "\"sid\":\"101\",";

        assertRejected(validator, sign(CLAIMS_JSON.replace(entry, "")), ErrorType.ACCESS_TOKEN_INVALID);
    }

    @Test
    void 양수_long_최대값을_허용한다() {
        String content = CLAIMS_JSON.replace("\"10\"", "\"" + Long.MAX_VALUE + "\"")
                .replace("\"101\"", "\"" + Long.MAX_VALUE + "\"");

        AccessTokenClaims claims = validator.readClaims(sign(content));

        assertThat(claims.userId().value()).isEqualTo(Long.MAX_VALUE);
        assertThat(claims.sessionId().value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void 설정된_발급자를_검증에_사용한다() {
        AuthTokenProperties customProperties = new AuthTokenProperties(
                "custom-issuer", properties.accessTokenTtl(), properties.sessionTtl(), TEST_SECRET_KEY_BASE64
        );
        AccessTokenValidator customValidator = new AccessTokenValidator(
                customProperties, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        String customContent = CLAIMS_JSON.replace("ai-block-clock", "custom-issuer");

        assertThat(customValidator.readClaims(sign(customContent)).userId().value()).isEqualTo(10L);
        assertRejected(customValidator, sign(CLAIMS_JSON), ErrorType.ACCESS_TOKEN_INVALID);
    }

    private static Stream<String> invalidIdJsonValues() {
        return Stream.of("null", "10", "true", "[]", "{}", "\"\"", "\"0\"", "\"-1\"", "\"+1\"",
                "\"01\"", "\" 10\"", "\"10 \"", "\"1.5\"", "\"abc\"", "\"9223372036854775808\"");
    }

    private String sign(String content) {
        return Jwts.builder().content(content.getBytes(StandardCharsets.UTF_8))
                .signWith(signingKey, Jwts.SIG.HS256).compact();
    }

    private AccessTokenValidator validatorAt(Instant now) {
        return new AccessTokenValidator(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void assertRejected(AccessTokenValidator sut, String token, ErrorType expectedError) {
        assertThatThrownBy(() -> sut.readClaims(token))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(expectedError);
                    assertThat(exception.getData()).isNull();
                    assertThat(exception.getCause()).isNull();
                    if (token != null && !token.isBlank()) {
                        assertThat(exception.getMessage()).doesNotContain(token);
                    }
                });
    }
}
