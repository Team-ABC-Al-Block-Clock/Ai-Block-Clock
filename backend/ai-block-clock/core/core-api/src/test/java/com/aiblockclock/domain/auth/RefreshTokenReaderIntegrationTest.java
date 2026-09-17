package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.auth.RefreshTokenEntity;
import com.aiblockclock.storage.auth.RefreshTokenRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class RefreshTokenReaderIntegrationTest {
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-24T10:00:00.123456Z");
    private static final Instant USED_AT = Instant.parse("2026-09-18T09:30:00.123456Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginSessionRepository loginSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenReader refreshTokenReader;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 해시값으로_연결된_세션과_만료_시각을_도메인_객체로_조회한다() {
        LoginSessionEntity session = saveSession();
        String refreshTokenHash = "a".repeat(64);
        RefreshTokenEntity saved = refreshTokenRepository.saveAndFlush(
                RefreshTokenEntity.of(session.getId(), refreshTokenHash, EXPIRES_AT)
        );
        entityManager.clear();

        RefreshToken found = refreshTokenReader.findByRefreshTokenHash(refreshTokenHash).orElseThrow();

        assertThat(found.id()).isEqualTo(RefreshTokenId.of(saved.getId()));
        assertThat(found.sessionId()).isEqualTo(LoginSessionId.of(session.getId()));
        assertThat(found.refreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.usedAt()).isNull();
    }

    @Test
    void 저장되지_않은_해시값을_조회하면_빈_결과를_반환한다() {
        assertThat(refreshTokenReader.findByRefreshTokenHash("b".repeat(64))).isEmpty();
    }

    @Test
    void 이미_사용한_토큰도_사용_시각을_포함해서_반환한다() {
        LoginSessionEntity session = saveSession();
        String refreshTokenHash = "c".repeat(64);
        RefreshTokenEntity saved = refreshTokenRepository.saveAndFlush(
                RefreshTokenEntity.of(session.getId(), refreshTokenHash, EXPIRES_AT)
        );
        entityManager.createQuery("""
                        update RefreshTokenEntity token
                        set token.usedAt = :usedAt
                        where token.id = :id
                        """)
                .setParameter("usedAt", USED_AT)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();

        RefreshToken found = refreshTokenReader.findByRefreshTokenHash(refreshTokenHash).orElseThrow();

        assertThat(found.usedAt()).isEqualTo(USED_AT);
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 한_세션에서_발급한_토큰들을_각각의_해시값으로_구분해서_조회한다() {
        LoginSessionEntity session = saveSession();
        String previousHash = "d".repeat(64);
        String currentHash = "e".repeat(64);
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity.of(session.getId(), previousHash, EXPIRES_AT));
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity.of(session.getId(), currentHash, EXPIRES_AT));
        entityManager.clear();

        RefreshToken foundPrevious = refreshTokenReader.findByRefreshTokenHash(previousHash).orElseThrow();
        RefreshToken foundCurrent = refreshTokenReader.findByRefreshTokenHash(currentHash).orElseThrow();

        assertThat(foundPrevious.id()).isNotEqualTo(foundCurrent.id());
        assertThat(foundPrevious.sessionId()).isEqualTo(foundCurrent.sessionId());
        assertThat(foundPrevious.refreshTokenHash()).isEqualTo(previousHash);
        assertThat(foundCurrent.refreshTokenHash()).isEqualTo(currentHash);
    }

    @Test
    void 생성한_원문의_해시를_저장하면_같은_원문으로_다시_조회할_수_있다() {
        LoginSessionEntity session = saveSession();
        String rawRefreshToken = refreshTokenGenerator.generate();
        String storedHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshTokenEntity saved = refreshTokenRepository.saveAndFlush(
                RefreshTokenEntity.of(session.getId(), storedHash, EXPIRES_AT)
        );
        entityManager.clear();

        String receivedHash = refreshTokenHasher.hash(rawRefreshToken);
        RefreshToken found = refreshTokenReader.findByRefreshTokenHash(receivedHash).orElseThrow();

        assertThat(receivedHash).isEqualTo(storedHash).matches("[0-9a-f]{64}");
        assertThat(found.id()).isEqualTo(RefreshTokenId.of(saved.getId()));
        assertThat(found.sessionId()).isEqualTo(LoginSessionId.of(session.getId()));
        assertThat(found.refreshTokenHash()).isEqualTo(storedHash);
    }

    @Test
    void 원문의_대소문자를_바꾸면_다른_해시로_조회하여_기록을_찾지_못한다() {
        LoginSessionEntity session = saveSession();
        String rawRefreshToken = "a1".repeat(32);
        String storedHash = refreshTokenHasher.hash(rawRefreshToken);
        refreshTokenRepository.saveAndFlush(RefreshTokenEntity.of(session.getId(), storedHash, EXPIRES_AT));
        entityManager.clear();

        String modifiedHash = refreshTokenHasher.hash(rawRefreshToken.toUpperCase(Locale.ROOT));

        assertThat(modifiedHash).isNotEqualTo(storedHash).matches("[0-9a-f]{64}");
        assertThat(refreshTokenReader.findByRefreshTokenHash(storedHash)).isPresent();
        assertThat(refreshTokenReader.findByRefreshTokenHash(modifiedHash)).isEmpty();
    }

    private LoginSessionEntity saveSession() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));

        return loginSessionRepository.saveAndFlush(LoginSessionEntity.of(user.getId(), EXPIRES_AT));
    }
}
