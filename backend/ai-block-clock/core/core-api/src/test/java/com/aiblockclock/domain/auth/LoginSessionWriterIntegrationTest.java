package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class LoginSessionWriterIntegrationTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");

    @Autowired
    private LoginSessionWriter loginSessionWriter;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 세션을_저장하고_DB가_생성한_번호와_사용자_정보를_반환한다() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        UserId userId = UserId.of(user.getId());

        LoginSession created = loginSessionWriter.append(userId, EXPIRES_AT);
        entityManager.clear();

        LoginSessionEntity persisted = loginSessionRepository.findById(created.id().value()).orElseThrow();

        assertThat(created.id().value()).isPositive();
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(created.revokedAt()).isNull();
        assertThat(persisted.getId()).isEqualTo(created.id().value());
        assertThat(persisted.getUserId()).isEqualTo(created.userId().value());
        assertThat(persisted.getExpiresAt()).isEqualTo(created.expiresAt());
        assertThat(persisted.getRevokedAt()).isNull();
        assertThat(persisted.getVersion()).isZero();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_사용자의_세션을_저장하면_외래_키_오류가_발생한다() {
        assertThatThrownBy(() -> loginSessionWriter.append(UserId.of(-1L), EXPIRES_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .satisfies(exception -> {
                    ConstraintViolationException cause = (ConstraintViolationException) exception.getCause();

                    assertThat(cause.getConstraintName()).containsIgnoringCase("fk_login_session_user");
                });
    }
}
