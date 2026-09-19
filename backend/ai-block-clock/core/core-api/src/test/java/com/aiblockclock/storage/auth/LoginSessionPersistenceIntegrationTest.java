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
class LoginSessionPersistenceIntegrationTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");

    @Autowired
    private EntityManager entityManager;

    @Test
    void 사용자와_연결된_세션_토큰_해시를_저장하고_조회한다() {
        UserEntity user = persistUser();
        String sessionTokenHash = "a".repeat(64);
        LoginSessionEntity session = LoginSessionEntity.of(user.getId(), sessionTokenHash, EXPIRES_AT);
        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        LoginSessionEntity found = entityManager.find(LoginSessionEntity.class, session.getId());

        assertThat(found.getUserId()).isEqualTo(user.getId());
        assertThat(found.getSessionTokenHash()).isEqualTo(sessionTokenHash);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.getRevokedAt()).isNull();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_세션_토큰_해시는_중복해서_저장할_수_없다() {
        UserEntity user = persistUser();
        String duplicateHash = "b".repeat(64);
        entityManager.persist(LoginSessionEntity.of(user.getId(), duplicateHash, EXPIRES_AT));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(LoginSessionEntity.of(user.getId(), duplicateHash, EXPIRES_AT));
            entityManager.flush();
        }).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void 존재하지_않는_사용자의_로그인_세션은_저장할_수_없다() {
        assertThatThrownBy(() -> {
            entityManager.persist(LoginSessionEntity.of(-1L, "c".repeat(64), EXPIRES_AT));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName()).containsIgnoringCase("fk_login_session_user"));
    }

    private UserEntity persistUser() {
        UserEntity user = UserEntity.of(
                "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000"
        );
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }
}
