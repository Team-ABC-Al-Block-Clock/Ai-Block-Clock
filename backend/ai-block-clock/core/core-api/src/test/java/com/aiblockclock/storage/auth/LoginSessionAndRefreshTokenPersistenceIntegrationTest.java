package com.aiblockclock.storage.auth;

import com.aiblockclock.storage.user.UserEntity;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class LoginSessionAndRefreshTokenPersistenceIntegrationTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");
    private static final Instant USED_AT = Instant.parse("2026-09-17T10:15:00.123456Z");

    @Autowired
    private EntityManager entityManager;

    @Test
    void 사용자와_연결된_세션과_토큰의_발급_정보를_저장하고_조회한다() {
        UserEntity user = persistUser();
        LoginSessionEntity session = persistSession(user.getId());
        String refreshTokenHash = "a".repeat(64);
        RefreshTokenEntity token = RefreshTokenEntity.of(session.getId(), refreshTokenHash, EXPIRES_AT);
        entityManager.persist(token);
        entityManager.flush();
        entityManager.clear();

        LoginSessionEntity foundSession = entityManager.find(LoginSessionEntity.class, session.getId());
        RefreshTokenEntity foundToken = entityManager.find(RefreshTokenEntity.class, token.getId());

        assertThat(foundSession.getId()).isPositive();
        assertThat(foundSession.getUserId()).isEqualTo(user.getId());
        assertThat(foundSession.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(foundSession.getRevokedAt()).isNull();
        assertThat(foundSession.getVersion()).isZero();
        assertThat(foundSession.getCreatedAt()).isNotNull();
        assertThat(foundSession.getUpdatedAt()).isNotNull();

        assertThat(foundToken.getId()).isPositive();
        assertThat(foundToken.getSessionId()).isEqualTo(foundSession.getId());
        assertThat(foundToken.getRefreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(foundToken.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(foundToken.getUsedAt()).isNull();
        assertThat(foundToken.getVersion()).isZero();
        assertThat(foundToken.getCreatedAt()).isNotNull();
        assertThat(foundToken.getUpdatedAt()).isNotNull();
    }

    @Test
    void 한_세션의_여러_토큰_기록과_사용_무효화_시각을_조회할_수_있다() {
        UserEntity user = persistUser();
        LoginSessionEntity session = persistSession(user.getId());
        RefreshTokenEntity usedToken = RefreshTokenEntity.of(session.getId(), "b".repeat(64), EXPIRES_AT);
        RefreshTokenEntity nextToken = RefreshTokenEntity.of(session.getId(), "c".repeat(64), EXPIRES_AT);
        entityManager.persist(usedToken);
        entityManager.persist(nextToken);
        entityManager.flush();

        // 과거 사용·무효화 기록을 준비해 nullable 시각 컬럼의 조회 매핑을 확인한다.
        entityManager.createQuery("update RefreshTokenEntity token set token.usedAt = :usedAt where token.id = :id")
                .setParameter("usedAt", USED_AT)
                .setParameter("id", usedToken.getId())
                .executeUpdate();
        entityManager.createQuery("update LoginSessionEntity session set session.revokedAt = :revokedAt where session.id = :id")
                .setParameter("revokedAt", USED_AT)
                .setParameter("id", session.getId())
                .executeUpdate();
        entityManager.clear();

        LoginSessionEntity foundSession = entityManager.find(LoginSessionEntity.class, session.getId());
        RefreshTokenEntity foundUsedToken = entityManager.find(RefreshTokenEntity.class, usedToken.getId());
        RefreshTokenEntity foundNextToken = entityManager.find(RefreshTokenEntity.class, nextToken.getId());

        assertThat(foundSession.getRevokedAt()).isEqualTo(USED_AT);
        assertThat(foundUsedToken.getUsedAt()).isEqualTo(USED_AT);
        assertThat(foundNextToken.getUsedAt()).isNull();
        assertThat(foundUsedToken.getSessionId()).isEqualTo(foundSession.getId());
        assertThat(foundNextToken.getSessionId()).isEqualTo(foundSession.getId());
    }

    @Test
    void 존재하지_않는_사용자의_로그인_세션은_저장할_수_없다() {
        assertThatThrownBy(() -> {
            entityManager.persist(LoginSessionEntity.of(-1L, EXPIRES_AT));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName()).containsIgnoringCase("fk_login_session_user"));
    }

    @Test
    void 존재하지_않는_세션의_Refresh_Token은_저장할_수_없다() {
        assertThatThrownBy(() -> {
            entityManager.persist(RefreshTokenEntity.of(-1L, "d".repeat(64), EXPIRES_AT));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName()).containsIgnoringCase("fk_refresh_token_session"));
    }

    @Test
    void 다른_세션이라도_같은_Refresh_Token_해시를_중복_저장할_수_없다() {
        UserEntity user = persistUser();
        LoginSessionEntity firstSession = persistSession(user.getId());
        LoginSessionEntity secondSession = persistSession(user.getId());
        String refreshTokenHash = "e".repeat(64);
        entityManager.persist(RefreshTokenEntity.of(firstSession.getId(), refreshTokenHash, EXPIRES_AT));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(RefreshTokenEntity.of(secondSession.getId(), refreshTokenHash, EXPIRES_AT));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName()).containsIgnoringCase("uq_refresh_token_hash"));
    }

    private UserEntity persistUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = UserEntity.of(walletAddress);
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private LoginSessionEntity persistSession(Long userId) {
        LoginSessionEntity session = LoginSessionEntity.of(userId, EXPIRES_AT);
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
