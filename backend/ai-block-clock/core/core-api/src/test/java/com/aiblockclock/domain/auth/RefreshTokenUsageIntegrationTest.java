package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.auth.RefreshTokenEntity;
import com.aiblockclock.storage.auth.RefreshTokenRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class RefreshTokenUsageIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456789Z");
    private static final Instant USED_AT = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant EXPIRES_AT = USED_AT.plusSeconds(600);

    @Autowired
    private RefreshTokenWriter writer;

    @Autowired
    private RefreshTokenReader reader;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private RefreshTokenGenerator generator;

    @Autowired
    private RefreshTokenHasher hasher;

    @Autowired
    private LoginSessionWriter sessionWriter;

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
    void 사용_처리는_서버_시각과_수정_시각을_기록하고_버전을_증가시킨다() {
        RefreshToken token = saveToken(saveSession());
        RefreshTokenEntity original = repository.findById(token.id().value()).orElseThrow();

        writer.modifyAsUsed(token);

        RefreshToken used = reader.findByRefreshTokenHash(token.refreshTokenHash()).orElseThrow();
        RefreshTokenEntity updated = repository.findById(token.id().value()).orElseThrow();
        assertThat(used.usedAt()).isEqualTo(USED_AT);
        assertThat(used.sessionId()).isEqualTo(token.sessionId());
        assertThat(used.refreshTokenHash()).isEqualTo(token.refreshTokenHash());
        assertThat(used.expiresAt()).isEqualTo(token.expiresAt());
        assertThat(updated.getUpdatedAt()).isEqualTo(USED_AT);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void 이전에_읽은_미사용_객체를_다시_보내도_사용_기록을_덮어쓰지_못한다() {
        RefreshToken token = saveToken(saveSession());
        writer.modifyAsUsed(token);
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        assertNotUsable(token);

        RefreshTokenEntity unchanged = repository.findById(token.id().value()).orElseThrow();
        assertThat(unchanged.getUsedAt()).isEqualTo(USED_AT);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(USED_AT);
        assertThat(unchanged.getVersion()).isEqualTo(1L);
    }

    @Test
    void DB_수정_조건도_만료_시각에_도달한_토큰을_거절한다() {
        RefreshToken token = saveToken(saveSession());

        int updatedCount = new TransactionTemplate(transactionManager).execute(status ->
                repository.updateUsedAtIfUsable(token.id().value(), token.sessionId().value(), token.expiresAt()));

        assertThat(updatedCount).isZero();
        assertThat(reader.findByRefreshTokenHash(token.refreshTokenHash())).contains(token);
        assertThat(repository.findById(token.id().value()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 토큰_번호가_같아도_세션_번호가_다르면_사용_처리하지_않는다() {
        RefreshToken token = saveToken(saveSession());
        LoginSession anotherSession = saveSession();
        RefreshToken mismatched = new RefreshToken(token.id(), anotherSession.id(),
                token.refreshTokenHash(), token.expiresAt(), null);

        assertNotUsable(mismatched);

        assertThat(reader.findByRefreshTokenHash(token.refreshTokenHash())).contains(token);
        assertThat(repository.findById(token.id().value()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 삭제된_토큰_기록은_사용_처리하지_못한다() {
        RefreshToken token = saveToken(saveSession());
        jdbcTemplate.update("delete from refresh_token where id = ?", token.id().value());

        assertNotUsable(token);

        assertThat(reader.findByRefreshTokenHash(token.refreshTokenHash())).isEmpty();
    }

    @Test
    void 같은_세션에서도_지정한_토큰만_사용_처리한다() {
        LoginSession session = saveSession();
        RefreshToken first = saveToken(session);
        RefreshToken second = saveToken(session);

        writer.modifyAsUsed(first);

        assertThat(reader.findByRefreshTokenHash(first.refreshTokenHash()).orElseThrow().usedAt())
                .isEqualTo(USED_AT);
        assertThat(reader.findByRefreshTokenHash(second.refreshTokenHash())).contains(second);
        assertThat(repository.findById(second.id().value()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 함께_실행한_트랜잭션이_실패하면_사용_처리도_취소된다() {
        RefreshToken token = saveToken(saveSession());
        RefreshTokenEntity original = repository.findById(token.id().value()).orElseThrow();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            writer.modifyAsUsed(token);

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        })).isInstanceOf(CoreException.class);

        assertThat(reader.findByRefreshTokenHash(token.refreshTokenHash())).contains(token);
        RefreshTokenEntity restored = repository.findById(token.id().value()).orElseThrow();
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    void 같은_토큰의_동시_사용_요청은_하나만_성공한다() throws Exception {
        RefreshToken token = saveToken(saveSession());
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> consumeOrConflict(token, barrier));
            var second = executor.submit(() -> consumeOrConflict(token, barrier));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(results.stream().filter(Boolean.TRUE::equals).count()).isEqualTo(1L);
            List<CoreException> failures = results.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_NOT_USABLE);
        }

        RefreshTokenEntity used = repository.findById(token.id().value()).orElseThrow();
        assertThat(used.getUsedAt()).isEqualTo(USED_AT);
        assertThat(used.getVersion()).isEqualTo(1L);
    }

    private Object consumeOrConflict(RefreshToken token, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            writer.modifyAsUsed(token);

            return Boolean.TRUE;
        } catch (CoreException exception) {
            return exception;
        }
    }

    private void assertNotUsable(RefreshToken token) {
        assertThatThrownBy(() -> writer.modifyAsUsed(token))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_NOT_USABLE));
    }

    private RefreshToken saveToken(LoginSession session) {
        String refreshTokenHash = hasher.hash(generator.generate());

        return writer.append(session.id(), refreshTokenHash, EXPIRES_AT);
    }

    private LoginSession saveSession() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        userIds.add(user.getId());

        return sessionWriter.append(UserId.of(user.getId()), EXPIRES_AT);
    }
}
