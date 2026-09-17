package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class LoginSessionRevocationIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456789Z");
    private static final Instant REVOKED_AT = NOW.truncatedTo(ChronoUnit.MICROS);
    private static final Instant EXPIRES_AT = REVOKED_AT.plusSeconds(600);

    @Autowired
    private LoginSessionWriter writer;

    @Autowired
    private LoginSessionReader reader;

    @Autowired
    private LoginSessionRepository repository;

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
    void 테스트에서_생성한_사용자와_세션만_정리한다() {
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from login_session where user_id = ?", userId);
            jdbcTemplate.update("delete from app_user where id = ?", userId);
        }
    }

    @Test
    void 종료_처리는_서버_시각과_수정_시각을_기록하고_버전을_증가시킨다() {
        LoginSession session = saveSession();
        LoginSessionEntity original = repository.findById(session.id().value()).orElseThrow();

        writer.modifyAsRevoked(session.id());

        LoginSession revoked = reader.findById(session.id()).orElseThrow();
        LoginSessionEntity updated = repository.findById(session.id().value()).orElseThrow();
        assertThat(revoked.revokedAt()).isEqualTo(REVOKED_AT);
        assertThat(revoked.userId()).isEqualTo(session.userId());
        assertThat(revoked.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(updated.getUpdatedAt()).isEqualTo(REVOKED_AT);
        assertThat(updated.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> revoked.checkUsable(REVOKED_AT))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
    }

    @Test
    void 반복해서_종료해도_최초_종료_시각을_유지한다() {
        LoginSession session = saveSession();
        writer.modifyAsRevoked(session.id());
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        writer.modifyAsRevoked(session.id());

        LoginSessionEntity unchanged = repository.findById(session.id().value()).orElseThrow();
        assertThat(unchanged.getRevokedAt()).isEqualTo(REVOKED_AT);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(REVOKED_AT);
        assertThat(unchanged.getVersion()).isEqualTo(1L);
    }

    @Test
    void 지정한_세션만_종료한다() {
        LoginSession first = saveSession();
        LoginSession second = writer.append(first.userId(), EXPIRES_AT);

        writer.modifyAsRevoked(first.id());

        assertThat(reader.findById(first.id()).orElseThrow().revokedAt()).isEqualTo(REVOKED_AT);
        assertThat(reader.findById(second.id())).contains(second);
        assertThat(repository.findById(second.id().value()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 존재하지_않는_세션의_종료는_다른_기록을_변경하지_않는다() {
        LoginSession session = saveSession();

        assertThatCode(() -> writer.modifyAsRevoked(LoginSessionId.of(-1L)))
                .doesNotThrowAnyException();

        assertThat(reader.findById(session.id())).contains(session);
    }

    @Test
    void 함께_실행한_트랜잭션이_실패하면_종료_처리도_취소된다() {
        LoginSession session = saveSession();
        LoginSessionEntity original = repository.findById(session.id().value()).orElseThrow();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            writer.modifyAsRevoked(session.id());

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        })).isInstanceOf(CoreException.class);

        assertThat(reader.findById(session.id())).contains(session);
        LoginSessionEntity restored = repository.findById(session.id().value()).orElseThrow();
        assertThat(restored.getVersion()).isZero();
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }

    @Test
    void 같은_세션의_동시_종료는_한_번만_기록한다() throws Exception {
        LoginSession session = saveSession();
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> revokeAfterBarrier(session.id(), barrier));
            var second = executor.submit(() -> revokeAfterBarrier(session.id(), barrier));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        LoginSessionEntity revoked = repository.findById(session.id().value()).orElseThrow();
        assertThat(revoked.getRevokedAt()).isEqualTo(REVOKED_AT);
        assertThat(revoked.getVersion()).isEqualTo(1L);
    }

    private Boolean revokeAfterBarrier(LoginSessionId sessionId, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        writer.modifyAsRevoked(sessionId);

        return Boolean.TRUE;
    }

    private LoginSession saveSession() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        userIds.add(user.getId());

        return writer.append(UserId.of(user.getId()), EXPIRES_AT);
    }
}
