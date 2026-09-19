package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class LoginSessionReaderIntegrationTest {
    private static final String SESSION_TOKEN_HASH = "a".repeat(64);
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");
    private static final Instant REVOKED_AT = Instant.parse("2026-09-18T09:30:00.123456Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    @Autowired
    private LoginSessionReader loginSessionReader;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 세션_번호로_사용자와_만료_시각을_도메인_객체로_조회한다() {
        UserEntity user = saveUser();
        LoginSessionEntity session = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(user.getId(), SESSION_TOKEN_HASH, EXPIRES_AT)
        );
        entityManager.clear();

        LoginSession found = loginSessionReader.findById(LoginSessionId.of(session.getId())).orElseThrow();

        assertThat(found.id()).isEqualTo(LoginSessionId.of(session.getId()));
        assertThat(found.userId()).isEqualTo(UserId.of(user.getId()));
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.revokedAt()).isNull();
    }

    @Test
    void 저장되지_않은_세션_번호를_조회하면_빈_결과를_반환한다() {
        assertThat(loginSessionReader.findById(LoginSessionId.of(-1L))).isEmpty();
    }

    @Test
    void 무효화된_세션도_무효화_시각을_포함해서_반환한다() {
        UserEntity user = saveUser();
        LoginSessionEntity session = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(user.getId(), SESSION_TOKEN_HASH, EXPIRES_AT)
        );
        entityManager.createQuery("""
                        update LoginSessionEntity session
                        set session.revokedAt = :revokedAt
                        where session.id = :id
                        """)
                .setParameter("revokedAt", REVOKED_AT)
                .setParameter("id", session.getId())
                .executeUpdate();
        entityManager.clear();

        LoginSession found = loginSessionReader.findById(LoginSessionId.of(session.getId())).orElseThrow();

        assertThat(found.revokedAt()).isEqualTo(REVOKED_AT);
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 같은_사용자의_여러_세션을_각각의_번호로_구분해서_조회한다() {
        UserEntity user = saveUser();
        LoginSessionEntity previousSession = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(user.getId(), "b".repeat(64), EXPIRES_AT)
        );
        LoginSessionEntity currentSession = loginSessionRepository.saveAndFlush(
                LoginSessionEntity.of(user.getId(), "c".repeat(64), EXPIRES_AT.plusSeconds(3600))
        );
        entityManager.clear();

        LoginSession foundPrevious = loginSessionReader.findById(
                LoginSessionId.of(previousSession.getId())
        ).orElseThrow();
        LoginSession foundCurrent = loginSessionReader.findById(
                LoginSessionId.of(currentSession.getId())
        ).orElseThrow();

        assertThat(foundPrevious.id()).isNotEqualTo(foundCurrent.id());
        assertThat(foundPrevious.userId()).isEqualTo(foundCurrent.userId());
        assertThat(foundPrevious.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(foundCurrent.expiresAt()).isEqualTo(EXPIRES_AT.plusSeconds(3600));
    }

    private UserEntity saveUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress));
    }
}
