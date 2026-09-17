package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class RequestAuthServiceIntegrationTest {
    @Autowired
    private RequestAuthService requestAuthService;

    @Autowired
    private AccessTokenGenerator accessTokenGenerator;

    @Autowired
    private AuthTokenProperties authTokenProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Test
    void 유효한_토큰이면_그_토큰이_가리키는_로그인_세션을_반환한다() {
        UserEntity user = saveUser();
        LoginSessionEntity session = saveSession(user.getId(), clock.instant().plus(7, ChronoUnit.DAYS));
        String rawAccessToken = issueToken(session);
        entityManager.clear();

        LoginSession authenticated = requestAuthService.authenticate(rawAccessToken);

        assertThat(authenticated.id()).isEqualTo(LoginSessionId.of(session.getId()));
        assertThat(authenticated.userId()).isEqualTo(UserId.of(user.getId()));
        assertThat(authenticated.revokedAt()).isNull();
    }

    @Test
    void 종료된_세션의_토큰은_아직_만료되지_않았어도_거절한다() {
        UserEntity user = saveUser();
        LoginSessionEntity session = saveSession(user.getId(), clock.instant().plus(7, ChronoUnit.DAYS));
        String rawAccessToken = issueToken(session);
        revokeSession(session.getId());
        entityManager.clear();

        assertThatThrownBy(() -> requestAuthService.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
    }

    @Test
    void 만료된_세션의_토큰은_거절한다() {
        UserEntity user = saveUser();
        LoginSessionEntity session = saveSession(user.getId(), clock.instant().plus(7, ChronoUnit.DAYS));
        String rawAccessToken = issueToken(session);
        expireSession(session.getId(), clock.instant().minusSeconds(1));
        entityManager.clear();

        assertThatThrownBy(() -> requestAuthService.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_EXPIRED));
    }

    @Test
    void 저장된_세션이_없는_토큰은_거절한다() {
        UserEntity user = saveUser();
        String rawAccessToken = signToken(user.getId(), Long.MAX_VALUE);

        assertThatThrownBy(() -> requestAuthService.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_NOT_USABLE));
    }

    @Test
    void 세션의_주인이_아닌_사용자를_담은_토큰은_거절한다() {
        UserEntity owner = saveUser();
        UserEntity other = saveUser();
        LoginSessionEntity session = saveSession(owner.getId(), clock.instant().plus(7, ChronoUnit.DAYS));
        String rawAccessToken = signToken(other.getId(), session.getId());
        entityManager.clear();

        assertThatThrownBy(() -> requestAuthService.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ACCESS_TOKEN_INVALID));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "not-a-token", "a.b.c"})
    void 토큰_원문이_올바르지_않으면_세션을_조회하지_않고_거절한다(String rawAccessToken) {
        assertThatThrownBy(() -> requestAuthService.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ACCESS_TOKEN_INVALID));
    }

    private UserEntity saveUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress));
    }

    private LoginSessionEntity saveSession(Long userId, Instant expiresAt) {
        return loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(userId, expiresAt.truncatedTo(ChronoUnit.MICROS))
        );
    }

    private String issueToken(LoginSessionEntity session) {
        return accessTokenGenerator.generate(LoginSessionEntityConverter.toDomain(session)).value();
    }

    // 생성기가 만들 수 없는 토큰이 필요한 경우에만 같은 비밀키로 직접 서명한다.
    private String signToken(long userId, long sessionId) {
        SecretKey signingKey = Keys.hmacShaKeyFor(
                Base64.getDecoder().decode(authTokenProperties.secretKeyBase64())
        );
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);

        return Jwts.builder()
                .issuer(authTokenProperties.issuer())
                .subject(Long.toString(userId))
                .claim("sid", Long.toString(sessionId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(authTokenProperties.accessTokenTtl())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private void revokeSession(Long sessionId) {
        entityManager.createQuery("""
                        update LoginSessionEntity session
                        set session.revokedAt = :revokedAt
                        where session.id = :id
                        """)
                .setParameter("revokedAt", clock.instant().truncatedTo(ChronoUnit.MICROS))
                .setParameter("id", sessionId)
                .executeUpdate();
    }

    private void expireSession(Long sessionId, Instant expiresAt) {
        entityManager.createQuery("""
                        update LoginSessionEntity session
                        set session.expiresAt = :expiresAt
                        where session.id = :id
                        """)
                .setParameter("expiresAt", expiresAt.truncatedTo(ChronoUnit.MICROS))
                .setParameter("id", sessionId)
                .executeUpdate();
    }
}
