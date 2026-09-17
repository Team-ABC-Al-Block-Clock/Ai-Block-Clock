package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.RefreshTokenEntity;
import com.aiblockclock.storage.auth.RefreshTokenRepository;
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
class RefreshTokenWriterIntegrationTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");

    @Autowired
    private LoginSessionWriter loginSessionWriter;

    @Autowired
    private RefreshTokenWriter refreshTokenWriter;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 세션_번호와_계산한_해시를_저장하고_토큰_발급_기록을_반환한다() {
        LoginSession session = saveSession();
        String rawRefreshToken = refreshTokenGenerator.generate();
        String refreshTokenHash = refreshTokenHasher.hash(rawRefreshToken);

        RefreshToken created = refreshTokenWriter.append(session.id(), refreshTokenHash, EXPIRES_AT);
        entityManager.clear();

        RefreshTokenEntity persisted = refreshTokenRepository.findById(created.id().value()).orElseThrow();

        assertThat(created.id().value()).isPositive();
        assertThat(created.sessionId()).isEqualTo(session.id());
        assertThat(created.refreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(created.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(created.usedAt()).isNull();
        assertThat(persisted.getId()).isEqualTo(created.id().value());
        assertThat(persisted.getSessionId()).isEqualTo(created.sessionId().value());
        assertThat(persisted.getRefreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(persisted.getExpiresAt()).isEqualTo(created.expiresAt());
        assertThat(persisted.getUsedAt()).isNull();
        assertThat(persisted.getVersion()).isZero();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void 존재하지_않는_세션에_토큰을_저장하면_외래_키_오류가_발생한다() {
        String refreshTokenHash = refreshTokenHasher.hash(refreshTokenGenerator.generate());

        assertThatThrownBy(() -> refreshTokenWriter.append(LoginSessionId.of(-1L), refreshTokenHash, EXPIRES_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .satisfies(exception -> {
                    ConstraintViolationException cause = (ConstraintViolationException) exception.getCause();

                    assertThat(cause.getConstraintName()).containsIgnoringCase("fk_refresh_token_session");
                });
    }

    @Test
    void 같은_해시값의_토큰_발급_기록을_중복_저장할_수_없다() {
        LoginSession session = saveSession();
        String refreshTokenHash = refreshTokenHasher.hash(refreshTokenGenerator.generate());
        refreshTokenWriter.append(session.id(), refreshTokenHash, EXPIRES_AT);

        assertThatThrownBy(() -> refreshTokenWriter.append(session.id(), refreshTokenHash, EXPIRES_AT))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasCauseInstanceOf(ConstraintViolationException.class)
                .satisfies(exception -> {
                    ConstraintViolationException cause = (ConstraintViolationException) exception.getCause();

                    assertThat(cause.getConstraintName()).containsIgnoringCase("uq_refresh_token_hash");
                });
    }

    private LoginSession saveSession() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));

        return loginSessionWriter.append(UserId.of(user.getId()), EXPIRES_AT);
    }
}
