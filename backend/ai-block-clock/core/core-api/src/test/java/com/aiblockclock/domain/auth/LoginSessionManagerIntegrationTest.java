package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class LoginSessionManagerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456789Z");
    private static final Instant REVOKED_AT = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant EXPIRES_AT = REVOKED_AT.plusSeconds(600);

    @Autowired
    private LoginSessionManager manager;

    @Autowired
    private LoginSessionWriter sessionWriter;

    @Autowired
    private LoginSessionReader sessionReader;

    @Autowired
    private LoginSessionRepository sessionRepository;

    @Autowired
    private RefreshTokenReader tokenReader;

    @Autowired
    private RefreshTokenGenerator generator;

    @Autowired
    private RefreshTokenHasher hasher;

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    private final List<Long> userIds = new ArrayList<>();

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 테스트에서_생성한_사용자와_세션과_토큰만_정리한다() {
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from refresh_token where session_id in "
                    + "(select id from login_session where user_id = ?)", userId);
            jdbcTemplate.update("delete from login_session where user_id = ?", userId);
            jdbcTemplate.update("delete from app_user where id = ?", userId);
        }
    }

    @Test
    void 새_세션과_그_번호에_연결된_토큰_기록을_함께_저장한다() {
        UserId userId = saveUser();
        String refreshTokenHash = newHash();

        LoginSession created = manager.create(userId, refreshTokenHash, EXPIRES_AT);

        RefreshToken token = tokenReader.findByRefreshTokenHash(refreshTokenHash).orElseThrow();
        assertThat(sessionReader.findById(created.id())).contains(created);
        assertThat(created.userId()).isEqualTo(userId);
        assertThat(created.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(created.revokedAt()).isNull();
        assertThat(token.sessionId()).isEqualTo(created.id());
        assertThat(token.refreshTokenHash()).isEqualTo(refreshTokenHash);
        assertThat(token.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token.usedAt()).isNull();
        assertThat(activeSessionCount(userId)).isEqualTo(1L);
        assertThat(tokenCount(userId)).isEqualTo(1L);
    }

    @Test
    void 이전_미종료_세션들을_모두_종료하고_새_세션_하나만_유효하게_남긴다() {
        UserId userId = saveUser();
        String oldHash = newHash();
        LoginSession first = manager.create(userId, oldHash, EXPIRES_AT);
        LoginSession second = sessionWriter.append(userId, EXPIRES_AT);

        LoginSession next = manager.create(userId, newHash(), EXPIRES_AT);

        for (LoginSession previous : List.of(first, second)) {
            LoginSessionEntity revoked = sessionRepository.findById(previous.id().value()).orElseThrow();
            assertThat(revoked.getRevokedAt()).isEqualTo(REVOKED_AT);
            assertThat(revoked.getUpdatedAt()).isEqualTo(REVOKED_AT);
            assertThat(revoked.getVersion()).isEqualTo(1L);
        }

        assertThat(sessionReader.findById(next.id()).orElseThrow().revokedAt()).isNull();
        assertThat(tokenReader.findByRefreshTokenHash(oldHash).orElseThrow().sessionId()).isEqualTo(first.id());
        assertThat(sessionCount(userId)).isEqualTo(3L);
        assertThat(activeSessionCount(userId)).isEqualTo(1L);
    }

    @Test
    void 다른_사용자의_세션은_종료하지_않는다() {
        UserId firstUser = saveUser();
        UserId secondUser = saveUser();
        LoginSession first = manager.create(firstUser, newHash(), EXPIRES_AT);
        LoginSession second = manager.create(secondUser, newHash(), EXPIRES_AT);

        manager.create(firstUser, newHash(), EXPIRES_AT);

        assertThat(sessionReader.findById(first.id()).orElseThrow().revokedAt()).isEqualTo(REVOKED_AT);
        assertThat(sessionReader.findById(second.id())).contains(second);
        assertThat(activeSessionCount(firstUser)).isEqualTo(1L);
        assertThat(activeSessionCount(secondUser)).isEqualTo(1L);
    }

    @Test
    void 이미_종료된_세션의_최초_종료_시각과_버전은_유지한다() {
        UserId userId = saveUser();
        LoginSession old = manager.create(userId, newHash(), EXPIRES_AT);
        sessionWriter.modifyAsRevoked(old.id());
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        manager.create(userId, newHash(), EXPIRES_AT);

        LoginSessionEntity unchanged = sessionRepository.findById(old.id().value()).orElseThrow();
        assertThat(unchanged.getRevokedAt()).isEqualTo(REVOKED_AT);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(REVOKED_AT);
        assertThat(unchanged.getVersion()).isEqualTo(1L);
        assertThat(activeSessionCount(userId)).isEqualTo(1L);
    }

    @Test
    void 토큰_저장이_실패하면_새_세션_저장과_이전_세션_종료도_취소된다() {
        UserId userId = saveUser();
        String duplicateHash = newHash();
        LoginSession old = manager.create(userId, duplicateHash, EXPIRES_AT);
        LoginSessionEntity original = sessionRepository.findById(old.id().value()).orElseThrow();

        assertThatThrownBy(() -> manager.create(userId, duplicateHash, EXPIRES_AT))
                .isInstanceOf(DataIntegrityViolationException.class);

        LoginSessionEntity restored = sessionRepository.findById(old.id().value()).orElseThrow();
        assertThat(sessionReader.findById(old.id())).contains(old);
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(restored.getVersion()).isZero();
        assertThat(sessionCount(userId)).isEqualTo(1L);
        assertThat(activeSessionCount(userId)).isEqualTo(1L);
        assertThat(tokenCount(userId)).isEqualTo(1L);
    }

    @Test
    void 존재하지_않는_사용자는_세션과_토큰을_만들지_못한다() {
        UserId missingUser = UserId.of(-1L);
        String refreshTokenHash = newHash();

        assertThatThrownBy(() -> manager.create(missingUser, refreshTokenHash, EXPIRES_AT))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REQUESTER_NOT_IDENTIFIED));

        assertThat(sessionCount(missingUser)).isZero();
        assertThat(tokenReader.findByRefreshTokenHash(refreshTokenHash)).isEmpty();
    }

    @Test
    void 같은_사용자의_동시_세션_생성은_마지막_세션_하나만_유효하게_남긴다() throws Exception {
        UserId userId = saveUser();
        String firstHash = newHash();
        String secondHash = newHash();
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfterBarrier(userId, firstHash, barrier));
            var second = executor.submit(() -> createAfterBarrier(userId, secondHash, barrier));
            LoginSession firstCreated = first.get(20, TimeUnit.SECONDS);
            LoginSession secondCreated = second.get(20, TimeUnit.SECONDS);

            List<LoginSession> persisted = List.of(
                    sessionReader.findById(firstCreated.id()).orElseThrow(),
                    sessionReader.findById(secondCreated.id()).orElseThrow()
            );
            assertThat(persisted.stream().filter(session -> session.revokedAt() == null).count()).isEqualTo(1L);
            assertThat(persisted.stream().filter(session -> REVOKED_AT.equals(session.revokedAt())).count())
                    .isEqualTo(1L);
            assertThat(tokenReader.findByRefreshTokenHash(firstHash).orElseThrow().sessionId())
                    .isEqualTo(firstCreated.id());
            assertThat(tokenReader.findByRefreshTokenHash(secondHash).orElseThrow().sessionId())
                    .isEqualTo(secondCreated.id());
        }

        assertThat(sessionCount(userId)).isEqualTo(2L);
        assertThat(activeSessionCount(userId)).isEqualTo(1L);
        assertThat(tokenCount(userId)).isEqualTo(2L);
    }

    @Test
    void 한_사용자의_잠금은_다른_사용자의_세션_생성을_막지_않는다() throws Exception {
        UserId lockedUser = saveUser();
        UserId anotherUser = saveUser();
        String refreshTokenHash = newHash();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                userReader.findByIdForUpdate(lockedUser).orElseThrow();
                locked.countDown();
                await(release);

                return Boolean.TRUE;
            }));

            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var creation = executor.submit(() -> manager.create(anotherUser, refreshTokenHash, EXPIRES_AT));

                assertThat(creation.get(10, TimeUnit.SECONDS).userId()).isEqualTo(anotherUser);
            } finally {
                release.countDown();
            }

            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(activeSessionCount(anotherUser)).isEqualTo(1L);
    }

    private LoginSession createAfterBarrier(UserId userId, String hash, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);

        return manager.create(userId, hash, EXPIRES_AT);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("사용자 잠금 테스트의 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(exception);
        }
    }

    private long activeSessionCount(UserId userId) {
        return jdbcTemplate.queryForObject("select count(*) from login_session "
                + "where user_id = ? and revoked_at is null and expires_at > ?", Long.class,
                userId.value(), Timestamp.from(REVOKED_AT));
    }

    private long sessionCount(UserId userId) {
        return jdbcTemplate.queryForObject("select count(*) from login_session where user_id = ?",
                Long.class, userId.value());
    }

    private long tokenCount(UserId userId) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where session_id in "
                + "(select id from login_session where user_id = ?)", Long.class, userId.value());
    }

    private String newHash() {
        return hasher.hash(generator.generate());
    }

    private UserId saveUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        userIds.add(user.getId());

        return UserId.of(user.getId());
    }
}
