package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserId;
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
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class TokenReissueIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00.123456Z");
    private static final Instant EXPIRES_AT = NOW.plusSeconds(7 * 24 * 60 * 60);

    @Autowired
    private WalletAuthService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private LoginSessionReader sessionReader;

    @Autowired
    private LoginSessionWriter sessionWriter;

    @MockitoSpyBean
    private RefreshTokenReader tokenReader;

    @Autowired
    private RefreshTokenHasher hasher;

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private RefreshTokenGenerator refreshTokenGenerator;

    @MockitoSpyBean
    private RefreshTokenWriter refreshTokenWriter;

    @MockitoSpyBean
    private AccessTokenGenerator accessTokenGenerator;

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
    void 만료된_Access_Token_대신_같은_세션의_새_토큰_두_개를_발급한다() {
        TestLogin login = login();
        AccessToken firstAccessToken = accessTokenGenerator.generate(login.session());
        Instant reissuedAt = NOW.plusSeconds(901);
        when(clock.instant()).thenReturn(reissuedAt);

        assertThatThrownBy(() -> service.authenticate(firstAccessToken.value()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ACCESS_TOKEN_EXPIRED));
        AuthTokens issued = service.reissue(login.rawRefreshToken());

        AccessTokenClaims claims = accessTokenValidator.readClaims(issued.accessToken().value());
        assertThat(claims.sessionId()).isEqualTo(login.session().id());
        assertThat(claims.userId()).isEqualTo(login.session().userId());
        assertThat(service.authenticate(issued.accessToken().value())).isEqualTo(login.session());
        assertThat(issued.accessToken().expiresAt())
                .isEqualTo(reissuedAt.truncatedTo(ChronoUnit.SECONDS).plusSeconds(900));
        assertThat(issued.refreshToken().value()).isNotEqualTo(login.rawRefreshToken());
        assertThat(issued.refreshToken().expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token(login.rawRefreshToken()).usedAt()).isEqualTo(reissuedAt);
        RefreshToken replacement = token(issued.refreshToken().value());
        assertThat(replacement.sessionId()).isEqualTo(login.session().id());
        assertThat(replacement.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(replacement.usedAt()).isNull();
        assertThat(sessionReader.findById(login.session().id())).contains(login.session());
        assertThat(tokenCount(login)).isEqualTo(2L);
        assertThat(sessionCount(login)).isEqualTo(1L);
    }

    @Test
    void 교체한_Refresh_Token은_다시_사용하지_못하고_새_토큰으로_계속_갱신한다() {
        TestLogin login = login();
        AuthTokens first = service.reissue(login.rawRefreshToken());

        assertRejected(ErrorType.REFRESH_TOKEN_ALREADY_USED, login.rawRefreshToken());
        when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
        AuthTokens second = service.reissue(first.refreshToken().value());

        assertThat(token(first.refreshToken().value()).usedAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(second.refreshToken().expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token(second.refreshToken().value()).sessionId()).isEqualTo(login.session().id());
        assertThat(tokenCount(login)).isEqualTo(3L);
        assertThat(sessionCount(login)).isEqualTo(1L);
    }

    @Test
    void 발급하지_않은_Refresh_Token은_거절한다() {
        assertRejected(ErrorType.REFRESH_TOKEN_NOT_USABLE, refreshTokenGenerator.generate());
    }

    @Test
    void Refresh_Token_만료_시각에_도달하면_재발급을_거절한다() {
        TestLogin login = login();
        when(clock.instant()).thenReturn(EXPIRES_AT);

        assertRejected(ErrorType.REFRESH_TOKEN_EXPIRED, login.rawRefreshToken());

        assertUnchanged(login);
    }

    @Test
    void Refresh_Token이_남아_있어도_로그인_세션이_만료되면_거절한다() {
        TestLogin login = login();
        String rawRefreshToken = refreshTokenGenerator.generate();
        refreshTokenWriter.append(login.session().id(), hasher.hash(rawRefreshToken), EXPIRES_AT.plusSeconds(60));
        when(clock.instant()).thenReturn(EXPIRES_AT);

        assertRejected(ErrorType.LOGIN_SESSION_EXPIRED, rawRefreshToken);

        assertThat(token(rawRefreshToken).usedAt()).isNull();
        assertThat(tokenCount(login)).isEqualTo(2L);
    }

    @Test
    void 종료된_로그인_세션에서는_재발급하지_않는다() {
        TestLogin login = login();
        sessionWriter.modifyAsRevoked(login.session().id());

        assertRejected(ErrorType.LOGIN_SESSION_REVOKED, login.rawRefreshToken());

        assertUnchanged(login);
    }

    @Test
    void 토큰_조회_후_세션이_종료되면_잠금_조회에서_거절한다() {
        TestLogin login = login();
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> sessionWriter.modifyAsRevoked(login.session().id()))
                        .get(10, TimeUnit.SECONDS);
            }

            return value;
        }).when(tokenReader).findByRefreshTokenHash(hasher.hash(login.rawRefreshToken()));

        assertRejected(ErrorType.LOGIN_SESSION_REVOKED, login.rawRefreshToken());

        org.mockito.Mockito.reset(tokenReader);
        assertUnchanged(login);
        assertThat(sessionReader.findById(login.session().id()).orElseThrow().revokedAt()).isEqualTo(NOW);
    }

    @Test
    void Service_검사_후_Refresh_Token이_만료되면_사용_처리에서_거절한다() {
        TestLogin login = login();
        String rawRefreshToken = refreshTokenGenerator.generate();
        Instant tokenExpiresAt = NOW.plusSeconds(60);
        refreshTokenWriter.append(login.session().id(), hasher.hash(rawRefreshToken), tokenExpiresAt);
        doAnswer(invocation -> {
            String value = (String) invocation.callRealMethod();
            when(clock.instant()).thenReturn(tokenExpiresAt);

            return value;
        }).when(refreshTokenGenerator).generate();

        assertRejected(ErrorType.REFRESH_TOKEN_EXPIRED, rawRefreshToken);

        assertThat(token(rawRefreshToken).usedAt()).isNull();
        assertThat(tokenCount(login)).isEqualTo(2L);
    }

    @Test
    void 새_Refresh_Token_저장_후_실패하면_기존_토큰_사용_처리도_취소한다() {
        TestLogin login = login();
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(refreshTokenWriter).append(any(LoginSessionId.class), any(String.class), any(Instant.class));

        assertRejected(ErrorType.INTERNAL_ERROR, login.rawRefreshToken());

        assertUnchanged(login);
    }

    @Test
    void Access_Token_생성이_실패하면_새_토큰_저장과_기존_사용_처리를_취소한다() {
        TestLogin login = login();
        doAnswer(invocation -> {
            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(accessTokenGenerator).generate(any(LoginSession.class));

        assertRejected(ErrorType.INTERNAL_ERROR, login.rawRefreshToken());

        assertUnchanged(login);
    }

    @Test
    void 세션_종료가_가까우면_Access_Token도_그_종료_시각까지만_발급한다() {
        TestLogin login = login();
        when(clock.instant()).thenReturn(EXPIRES_AT.minusSeconds(10));

        AuthTokens issued = service.reissue(login.rawRefreshToken());

        assertThat(issued.accessToken().expiresAt()).isEqualTo(EXPIRES_AT.truncatedTo(ChronoUnit.SECONDS));
        assertThat(issued.refreshToken().expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(token(issued.refreshToken().value()).expiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void 같은_Refresh_Token의_동시_재발급은_하나만_성공한다() throws Exception {
        TestLogin login = login();
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            Object value = invocation.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);

            return value;
        }).when(tokenReader).findByRefreshTokenHash(hasher.hash(login.rawRefreshToken()));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reissueOrConflict(login.rawRefreshToken()));
            var second = executor.submit(() -> reissueOrConflict(login.rawRefreshToken()));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            List<AuthTokens> successes = results.stream().filter(AuthTokens.class::isInstance)
                    .map(AuthTokens.class::cast).toList();
            List<CoreException> failures = results.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getErrorType()).isEqualTo(ErrorType.REFRESH_TOKEN_NOT_USABLE);
            assertThat(token(successes.getFirst().refreshToken().value()).usedAt()).isNull();
        }

        org.mockito.Mockito.reset(tokenReader);
        assertThat(token(login.rawRefreshToken()).usedAt()).isEqualTo(NOW);
        assertThat(tokenCount(login)).isEqualTo(2L);
        assertThat(sessionCount(login)).isEqualTo(1L);
    }

    @Test
    void 재발급이_세션을_잠근_동안_종료_처리는_기다리고_커밋_후_종료한다() throws Exception {
        TestLogin login = login();
        CountDownLatch tokenGenerated = new CountDownLatch(1);
        CountDownLatch releaseReissue = new CountDownLatch(1);
        CountDownLatch revokeStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            AccessToken generated = (AccessToken) invocation.callRealMethod();
            tokenGenerated.countDown();

            if (!releaseReissue.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("재발급 트랜잭션 종료 대기 시간이 초과됐습니다.");
            }

            return generated;
        }).when(accessTokenGenerator).generate(any(LoginSession.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var reissuing = executor.submit(() -> service.reissue(login.rawRefreshToken()));

            try {
                assertThat(tokenGenerated.await(10, TimeUnit.SECONDS)).isTrue();
                var revoking = executor.submit(() -> {
                    revokeStarted.countDown();
                    service.logout(login.rawRefreshToken());
                });
                assertThat(revokeStarted.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> revoking.get(250, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);

                releaseReissue.countDown();
                AuthTokens issued = reissuing.get(10, TimeUnit.SECONDS);
                revoking.get(10, TimeUnit.SECONDS);

                assertThatThrownBy(() -> service.authenticate(issued.accessToken().value()))
                        .isInstanceOfSatisfying(CoreException.class, exception ->
                                assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
            } finally {
                releaseReissue.countDown();
            }
        }

        assertThat(sessionReader.findById(login.session().id()).orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThat(tokenCount(login)).isEqualTo(2L);
    }

    private TestLogin login() {
        String address = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(address));
        userIds.add(user.getId());
        String rawRefreshToken = refreshTokenGenerator.generate();
        LoginSession session = new TransactionTemplate(transactionManager).execute(status -> {
            LoginSession saved = sessionWriter.append(UserId.of(user.getId()), EXPIRES_AT);
            refreshTokenWriter.append(saved.id(), hasher.hash(rawRefreshToken), EXPIRES_AT);

            return saved;
        });

        return new TestLogin(session, rawRefreshToken);
    }

    private RefreshToken token(String rawRefreshToken) {
        return tokenReader.findByRefreshTokenHash(hasher.hash(rawRefreshToken)).orElseThrow();
    }

    private void assertUnchanged(TestLogin login) {
        assertThat(token(login.rawRefreshToken()).usedAt()).isNull();
        assertThat(tokenCount(login)).isEqualTo(1L);
        assertThat(sessionCount(login)).isEqualTo(1L);
    }

    private void assertRejected(ErrorType expected, String rawRefreshToken) {
        assertThatThrownBy(() -> service.reissue(rawRefreshToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private Object reissueOrConflict(String rawRefreshToken) {
        try {
            return service.reissue(rawRefreshToken);
        } catch (CoreException exception) {
            return exception;
        }
    }

    private long tokenCount(TestLogin login) {
        return jdbcTemplate.queryForObject("select count(*) from refresh_token where session_id = ?",
                Long.class, login.session().id().value());
    }

    private long sessionCount(TestLogin login) {
        return jdbcTemplate.queryForObject("select count(*) from login_session where user_id = ?",
                Long.class, login.session().userId().value());
    }

    private record TestLogin(LoginSession session, String rawRefreshToken) {
    }
}
