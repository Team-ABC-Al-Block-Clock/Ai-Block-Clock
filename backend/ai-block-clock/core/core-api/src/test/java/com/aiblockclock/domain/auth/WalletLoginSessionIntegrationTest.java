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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class WalletLoginSessionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456Z");

    @Autowired
    private WalletAuthService service;
    @Autowired
    private LoginSessionWriter sessionWriter;
    @Autowired
    private LoginSessionReader sessionReader;
    @Autowired
    private LoginSessionRepository sessionRepository;
    @Autowired
    private RefreshTokenReader tokenReader;
    @Autowired
    private RefreshTokenHasher hasher;
    @Autowired
    private AccessTokenValidator accessTokenValidator;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    private UserReader userReader;
    @MockitoSpyBean
    private RefreshTokenWriter tokenWriter;
    @MockitoBean
    private Clock clock;

    private final List<TestUser> users = new ArrayList<>();

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 테스트에서_만든_기록만_정리한다() {
        for (TestUser user : users) {
            jdbcTemplate.update("delete from refresh_token where session_id in "
                    + "(select id from login_session where user_id = ?)", user.id().value());
            jdbcTemplate.update("delete from login_session where user_id = ?", user.id().value());
            jdbcTemplate.update("delete from wallet_auth_message where wallet_address = ?", user.address());
            jdbcTemplate.update("delete from app_user where id = ?", user.id().value());
        }
    }

    @Test
    void 로그인은_같은_세션에_토큰을_연결하고_해시만_저장한다() {
        TestUser user = saveUser();
        WalletLogin login = login(user);
        LoginSession session = session(login);
        RefreshToken token = tokenReader.findByRefreshTokenHash(hasher.hash(login.refreshToken().value()))
                .orElseThrow();

        assertThat(session.userId()).isEqualTo(user.id());
        assertThat(session.expiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60));
        assertThat(token.sessionId()).isEqualTo(session.id());
        assertThat(token.expiresAt()).isEqualTo(session.expiresAt());
        assertThat(token.refreshTokenHash()).isNotEqualTo(login.refreshToken().value());
        assertThat(token.usedAt()).isNull();
        assertThat(activeSessionCount(user)).isEqualTo(1L);
    }

    @Test
    void 재로그인은_이전_미종료_세션들을_모두_종료한다() {
        TestUser user = saveUser();
        LoginSession first = session(login(user));
        LoginSession second = sessionWriter.append(user.id(), first.expiresAt());

        LoginSession next = session(login(user));

        for (LoginSession previous : List.of(first, second)) {
            LoginSessionEntity revoked = sessionRepository.findById(previous.id().value()).orElseThrow();
            assertThat(revoked.getRevokedAt()).isEqualTo(NOW);
            assertThat(revoked.getUpdatedAt()).isEqualTo(NOW);
            assertThat(revoked.getVersion()).isEqualTo(1L);
        }
        assertThat(next.revokedAt()).isNull();
        assertThat(sessionCount(user)).isEqualTo(3L);
        assertThat(activeSessionCount(user)).isEqualTo(1L);
    }

    @Test
    void 재로그인은_다른_사용자의_세션을_종료하지_않는다() {
        TestUser firstUser = saveUser();
        TestUser secondUser = saveUser();
        LoginSession first = session(login(firstUser));
        LoginSession second = session(login(secondUser));

        login(firstUser);

        assertThat(sessionReader.findById(first.id()).orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThat(sessionReader.findById(second.id())).contains(second);
    }

    @Test
    void 이미_종료된_세션의_최초_종료_시각과_버전은_유지한다() {
        TestUser user = saveUser();
        LoginSession old = session(login(user));
        sessionWriter.modifyAsRevoked(old.id());
        when(clock.instant()).thenReturn(NOW.plusSeconds(30));

        login(user);

        LoginSessionEntity unchanged = sessionRepository.findById(old.id().value()).orElseThrow();
        assertThat(unchanged.getRevokedAt()).isEqualTo(NOW);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(NOW);
        assertThat(unchanged.getVersion()).isEqualTo(1L);
        assertThat(activeSessionCount(user)).isEqualTo(1L);
    }

    @Test
    void 토큰_저장_후_실패하면_새_세션과_이전_세션_종료를_취소한다() {
        TestUser user = saveUser();
        LoginSession old = session(login(user));
        LoginSessionEntity original = sessionRepository.findById(old.id().value()).orElseThrow();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(tokenWriter).append(any(LoginSessionId.class), any(String.class), any(Instant.class));

        assertThatThrownBy(() -> login(user)).isInstanceOf(CoreException.class);

        LoginSessionEntity restored = sessionRepository.findById(old.id().value()).orElseThrow();
        assertThat(sessionReader.findById(old.id())).contains(old);
        assertThat(restored.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(restored.getVersion()).isZero();
        assertThat(sessionCount(user)).isEqualTo(1L);
        assertThat(tokenCount(user)).isEqualTo(1L);
    }

    @Test
    void 잠금_조회에서_사용자가_없으면_메시지_사용_처리도_취소한다() {
        TestUser user = saveUser();
        when(userReader.findByIdForUpdate(user.id())).thenReturn(java.util.Optional.empty());
        WalletAuthMessage message = service.issueMessage(user.address());

        assertThatThrownBy(() -> service.login(user.address(), sign(message, user.wallet())))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.REQUESTER_NOT_IDENTIFIED));

        assertThat(sessionCount(user)).isZero();
        assertThat(tokenCount(user)).isZero();
        assertThat(jdbcTemplate.queryForObject("select used_at from wallet_auth_message where wallet_address = ?",
                Instant.class, user.address())).isNull();
    }

    @Test
    void 로그인과_메시지_재발급이_겹쳐도_유효한_세션은_하나다() throws Exception {
        TestUser user = saveUser();
        WalletAuthMessage firstMessage = service.issueMessage(user.address());
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            firstLocked.countDown();
            await(release);
            return result;
        }).when(userReader).findByIdForUpdate(user.id());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.login(user.address(), sign(firstMessage, user.wallet())));
            try {
                assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
                var second = executor.submit(() -> {
                    try {
                        return (Object) login(user);
                    } catch (CoreException exception) {
                        return exception;
                    }
                });
                release.countDown();
                LoginSession firstSession = session(first.get(20, TimeUnit.SECONDS));
                Object result = second.get(20, TimeUnit.SECONDS);
                WalletLogin secondLogin;
                if (result instanceof CoreException exception) {
                    // 이전 버전을 읽은 발급 요청은 충돌로 거절하고 커밋 후 다시 발급한다.
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_CONCURRENTLY_MODIFIED);
                    assertThat(activeSessionCount(user)).isEqualTo(1L);
                    assertThat(sessionCount(user)).isEqualTo(1L);
                    secondLogin = login(user);
                } else {
                    secondLogin = (WalletLogin) result;
                }
                LoginSession secondSession = session(secondLogin);
                assertThat(sessionReader.findById(firstSession.id()).orElseThrow().revokedAt()).isEqualTo(NOW);
                assertThat(secondSession.revokedAt()).isNull();
            } finally {
                release.countDown();
            }
        }
        assertThat(activeSessionCount(user)).isEqualTo(1L);
        assertThat(sessionCount(user)).isEqualTo(2L);
        assertThat(tokenCount(user)).isEqualTo(2L);
    }

    @Test
    void 한_사용자의_잠금은_다른_사용자의_로그인을_막지_않는다() throws Exception {
        TestUser lockedUser = saveUser();
        TestUser anotherUser = saveUser();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                userReader.findByIdForUpdate(lockedUser.id()).orElseThrow();
                locked.countDown();
                await(release);
                return Boolean.TRUE;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(executor.submit(() -> login(anotherUser)).get(10, TimeUnit.SECONDS).user().id())
                        .isEqualTo(anotherUser.id());
            } finally {
                release.countDown();
            }
            assertThat(holder.get(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private WalletLogin login(TestUser user) {
        WalletAuthMessage message = service.issueMessage(user.address());
        return service.login(user.address(), sign(message, user.wallet()));
    }

    private LoginSession session(WalletLogin login) {
        return sessionReader.findById(accessTokenValidator.readClaims(login.accessToken().value()).sessionId())
                .orElseThrow();
    }

    private TestUser saveUser() {
        ECKeyPair wallet = ECKeyPair.create(new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16)
                .add(BigInteger.ONE));
        String address = "0x" + Keys.getAddress(wallet.getPublicKey());
        UserEntity saved = userRepository.saveAndFlush(UserEntity.of(address));
        TestUser user = new TestUser(UserId.of(saved.getId()), address, wallet);
        users.add(user);
        return user;
    }

    private String sign(WalletAuthMessage message, ECKeyPair wallet) {
        Sign.SignatureData data = Sign.signPrefixedMessage(message.message().getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();
        return "0x" + hex.formatHex(data.getR()) + hex.formatHex(data.getS()) + hex.formatHex(data.getV());
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("로그인 테스트의 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private long activeSessionCount(TestUser user) {
        return jdbcTemplate.queryForObject("select count(*) from login_session "
                + "where user_id = ? and revoked_at is null", Long.class, user.id().value());
    }

    private long sessionCount(TestUser user) {
        return jdbcTemplate.queryForObject("select count(*) from login_session where user_id = ?",
                Long.class, user.id().value());
    }

    private long tokenCount(TestUser user) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where session_id in "
                + "(select id from login_session where user_id = ?)", Long.class, user.id().value());
    }

    private record TestUser(UserId id, String address, ECKeyPair wallet) {
    }
}
