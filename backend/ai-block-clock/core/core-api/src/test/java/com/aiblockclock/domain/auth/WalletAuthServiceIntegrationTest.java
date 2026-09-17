package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.payment.LedgerAccountWriter;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class WalletAuthServiceIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456Z");

    @Autowired
    private WalletAuthService service;

    @Autowired
    private WalletAuthMessageReader messageReader;

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private WalletAuthMessageRepository messageRepository;

    @Autowired
    private LoginSessionReader loginSessionReader;

    @Autowired
    private RefreshTokenReader refreshTokenReader;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private AuthTokenProperties authTokenProperties;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private LedgerAccountWriter accountWriter;

    @MockitoSpyBean
    private WalletSignatureValidator signatureValidator;

    @MockitoSpyBean
    private RefreshTokenWriter refreshTokenWriter;

    @MockitoSpyBean
    private AccessTokenGenerator accessTokenGenerator;

    private final List<String> walletAddresses = new ArrayList<>();

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 테스트에서_만든_메시지와_사용자와_계정만_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String address : walletAddresses) {
                userRepository.findByWalletAddressIgnoreCase(address).ifPresent(user -> {
                    deleteLoginSessions(user.getId());
                    accountRepository.findByAccountTypeAndReferenceId(
                            LedgerAccountType.ABC_BALANCE, Long.toString(user.getId())
                    ).ifPresent(accountRepository::delete);
                    userRepository.delete(user);
                });
                messageRepository.findByWalletAddress(address).ifPresent(messageRepository::delete);
            }
        });
    }

    @Test
    void 메시지_발급은_원문을_저장하지만_아직_사용자를_가입시키지_않는다() {
        ECKeyPair wallet = newWallet();

        WalletAuthMessage issued = service.issueMessage(address(wallet));

        assertThat(messageReader.findByWalletAddress(address(wallet))).contains(issued);
        assertThat(issued.usedAt()).isNull();
        assertThat(issued.message()).contains(issued.nonce());
        assertThat(userReader.findByWalletAddress(address(wallet))).isEmpty();
    }

    @Test
    void 검증된_신규_로그인은_메시지를_사용하고_사용자와_ABC_계정을_생성한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        WalletLogin login = service.login(address(wallet).toUpperCase(Locale.ROOT), sign(issued, wallet));
        User loggedIn = login.user();

        assertThat(loggedIn.walletAddress()).isEqualTo(address(wallet));
        assertThat(userReader.findByWalletAddress(address(wallet)).orElseThrow().id()).isEqualTo(loggedIn.id());
        assertSessionAndRefreshToken(login, loggedIn, NOW.plusSeconds(60));
        LedgerAccountEntity account = account(loggedIn.id());
        assertThat(account.getBalance()).isEqualByComparingTo("0.000000");
        assertThat(account.getReferenceId()).isEqualTo(Long.toString(loggedIn.id().value()));
        assertThat(messageReader.findByWalletAddress(address(wallet)).orElseThrow().usedAt())
                .isEqualTo(NOW.plusSeconds(60));
    }

    @Test
    void 기존_사용자의_다음_로그인은_사용자와_ABC_계정을_다시_만들지_않는다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage first = service.issueMessage(address(wallet));
        WalletLogin firstLogin = service.login(address(wallet), sign(first, wallet));
        User original = firstLogin.user();
        Long accountId = account(original.id()).getId();
        WalletAuthMessage next = service.issueMessage(address(wallet));

        WalletLogin secondLogin = service.login(address(wallet), sign(next, wallet));

        assertThat(secondLogin.user().id()).isEqualTo(original.id());
        assertThat(account(secondLogin.user().id()).getId()).isEqualTo(accountId);
        assertThat(messageReader.findByWalletAddress(address(wallet)).orElseThrow().usedAt()).isEqualTo(NOW);
        assertSessionAndRefreshToken(secondLogin, original, NOW);
        assertThat(secondLogin.refreshToken().value()).isNotEqualTo(firstLogin.refreshToken().value());
        assertThat(session(firstLogin).revokedAt()).isEqualTo(NOW);
        assertThat(session(secondLogin).revokedAt()).isNull();
    }

    @Test
    void 다른_지갑의_서명은_메시지를_사용하거나_사용자를_생성하지_않는다() {
        ECKeyPair wallet = newWallet();
        ECKeyPair otherWallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));

        assertError(ErrorType.WALLET_SIGNATURE_INVALID, address(wallet), sign(issued, otherWallet));

        assertUnusedAndUnregistered(issued);
        assertThat(userReader.findByWalletAddress(address(otherWallet))).isEmpty();
    }

    @Test
    void 만료_시각에_도달한_메시지는_로그인에_사용하지_못한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        when(clock.instant()).thenReturn(issued.expiresAt());

        assertError(ErrorType.WALLET_AUTH_MESSAGE_EXPIRED, address(wallet), sign(issued, wallet));

        assertUnusedAndUnregistered(issued);
    }

    @Test
    void 사용한_서명을_다시_보내면_거절한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        String signature = sign(issued, wallet);
        User original = service.login(address(wallet), signature).user();
        Long accountId = account(original.id()).getId();

        assertError(ErrorType.WALLET_AUTH_MESSAGE_ALREADY_USED, address(wallet), signature);

        assertThat(userReader.findByWalletAddress(address(wallet)).orElseThrow().id()).isEqualTo(original.id());
        assertThat(account(original.id()).getId()).isEqualTo(accountId);
    }

    @Test
    void 재발급_전_메시지의_서명으로_로그인하면_거절한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage previous = service.issueMessage(address(wallet));
        String previousSignature = sign(previous, wallet);
        WalletAuthMessage next = service.issueMessage(address(wallet));

        assertError(ErrorType.WALLET_SIGNATURE_INVALID, address(wallet), previousSignature);

        assertUnusedAndUnregistered(next);
    }

    @Test
    void ABC_계정을_저장한_뒤_실패해도_가입과_메시지_사용_처리를_모두_취소한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        AtomicReference<UserId> attemptedUserId = new AtomicReference<>();
        doAnswer(invocation -> {
            attemptedUserId.set(invocation.getArgument(0));
            invocation.callRealMethod();

            throw new CoreException(ErrorType.LEDGER_INVALID_TRANSACTION);
        }).when(accountWriter).createAbcBalance(any(UserId.class));

        assertError(ErrorType.LEDGER_INVALID_TRANSACTION, address(wallet), sign(issued, wallet));

        assertUnusedAndUnregistered(issued);
        assertThat(attemptedUserId.get()).isNotNull();
        assertThat(accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(attemptedUserId.get().value()))).isEmpty();
        assertThat(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 서명_검증_중_만료되면_사용_처리_단계에서_다시_거절한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        String signature = sign(issued, wallet);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            when(clock.instant()).thenReturn(issued.expiresAt());

            return null;
        }).when(signatureValidator).check(any(WalletAuthMessage.class), eq(signature));

        assertError(ErrorType.WALLET_AUTH_MESSAGE_EXPIRED, address(wallet), signature);

        assertUnusedAndUnregistered(issued);
    }

    @Test
    void 서명_검증_직후_재발급하면_새_메시지를_사용_처리하지_못한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage previous = service.issueMessage(address(wallet));
        String signature = sign(previous, wallet);
        AtomicReference<WalletAuthMessage> replacement = new AtomicReference<>();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            replacement.set(service.issueMessage(address(wallet)));

            return null;
        }).when(signatureValidator).check(any(WalletAuthMessage.class), eq(signature));

        assertError(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE, address(wallet), signature);

        assertUnusedAndUnregistered(replacement.get());
    }

    @Test
    void Refresh_Token_저장이_실패하면_가입과_메시지_사용_처리를_모두_취소한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        failOnRefreshTokenAppend();

        assertError(ErrorType.INTERNAL_ERROR, address(wallet), sign(issued, wallet));

        assertUnusedAndUnregistered(issued);
        assertThat(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getVersion()).isZero();
    }

    @Test
    void 재로그인의_Refresh_Token_저장이_실패하면_이전_세션도_종료되지_않는다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage first = service.issueMessage(address(wallet));
        WalletLogin firstLogin = service.login(address(wallet), sign(first, wallet));
        WalletAuthMessage next = service.issueMessage(address(wallet));
        String nextSignature = sign(next, wallet);
        failOnRefreshTokenAppend();

        assertError(ErrorType.INTERNAL_ERROR, address(wallet), nextSignature);

        assertThat(session(firstLogin).revokedAt()).isNull();
        assertThat(messageReader.findByWalletAddress(address(wallet)).orElseThrow().usedAt()).isNull();
    }

    @Test
    void Access_Token_생성이_실패하면_신규_가입과_로그인_저장을_모두_취소한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        AtomicReference<LoginSession> attemptedSession = failOnAccessTokenGeneration();

        assertError(ErrorType.INTERNAL_ERROR, address(wallet), sign(issued, wallet));

        assertUnusedAndUnregistered(issued);
        LoginSession failedSession = attemptedSession.get();
        assertNoSessionOrRefreshToken(failedSession);
        assertThat(accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(failedSession.userId().value()))).isEmpty();
    }

    @Test
    void Access_Token_생성이_실패한_재로그인은_이전_세션을_유지한다() {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage first = service.issueMessage(address(wallet));
        WalletLogin firstLogin = service.login(address(wallet), sign(first, wallet));
        Long accountId = account(firstLogin.user().id()).getId();
        WalletAuthMessage next = service.issueMessage(address(wallet));
        AtomicReference<LoginSession> attemptedSession = failOnAccessTokenGeneration();

        assertError(ErrorType.INTERNAL_ERROR, address(wallet), sign(next, wallet));

        assertThat(session(firstLogin).revokedAt()).isNull();
        assertThat(service.authenticate(firstLogin.accessToken().value()).id()).isEqualTo(session(firstLogin).id());
        assertThat(messageReader.findByWalletAddress(address(wallet))).contains(next);
        assertThat(account(firstLogin.user().id()).getId()).isEqualTo(accountId);
        assertNoSessionOrRefreshToken(attemptedSession.get());
    }

    @Test
    void 같은_서명의_동시_신규_로그인은_하나만_가입을_완료한다() throws Exception {
        ECKeyPair wallet = newWallet();
        WalletAuthMessage issued = service.issueMessage(address(wallet));
        String signature = sign(issued, wallet);
        CyclicBarrier barrier = new CyclicBarrier(2);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            barrier.await(10, TimeUnit.SECONDS);

            return null;
        }).when(signatureValidator).check(any(WalletAuthMessage.class), eq(signature));
        User successful;

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> loginOrConflict(address(wallet), signature));
            var second = executor.submit(() -> loginOrConflict(address(wallet), signature));
            List<Object> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            List<WalletLogin> successes = results.stream().filter(WalletLogin.class::isInstance)
                    .map(WalletLogin.class::cast).toList();
            List<CoreException> failures = results.stream().filter(CoreException.class::isInstance)
                    .map(CoreException.class::cast).toList();
            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().getErrorType()).isEqualTo(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE);
            successful = successes.getFirst().user();
        }

        assertThat(userReader.findByWalletAddress(address(wallet)).orElseThrow().id()).isEqualTo(successful.id());
        assertThat(account(successful.id()).getBalance()).isEqualByComparingTo("0.000000");
        assertThat(messageReader.findByWalletAddress(address(wallet)).orElseThrow().usedAt()).isEqualTo(NOW);
        assertThat(messageRepository.findByWalletAddress(address(wallet)).orElseThrow().getVersion()).isEqualTo(1L);
    }

    private Object loginOrConflict(String address, String signature) {
        try {
            return service.login(address, signature);
        } catch (CoreException exception) {
            return exception;
        }
    }

    // 로그인 트랜잭션의 마지막 저장만 실패시켜 앞선 변경이 함께 취소되는지 확인한다.
    private void failOnRefreshTokenAppend() {
        doAnswer(invocation -> {
            invocation.callRealMethod();

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(refreshTokenWriter).append(any(LoginSessionId.class), any(String.class), any(Instant.class));
    }

    private AtomicReference<LoginSession> failOnAccessTokenGeneration() {
        AtomicReference<LoginSession> attemptedSession = new AtomicReference<>();
        doAnswer(invocation -> {
            attemptedSession.set(invocation.getArgument(0));

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(accessTokenGenerator).generate(any(LoginSession.class));

        return attemptedSession;
    }

    private void assertNoSessionOrRefreshToken(LoginSession failedSession) {
        assertThat(failedSession).isNotNull();
        assertThat(loginSessionReader.findById(failedSession.id())).isEmpty();
        Long refreshTokenCount = new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("""
                                select count(token) from RefreshTokenEntity token
                                where token.sessionId = :sessionId
                                """, Long.class)
                        .setParameter("sessionId", failedSession.id().value())
                        .getSingleResult());

        assertThat(refreshTokenCount).isZero();
    }

    private void deleteLoginSessions(Long userId) {
        entityManager.createQuery("""
                        delete from RefreshTokenEntity token
                        where token.sessionId in (
                            select session.id from LoginSessionEntity session where session.userId = :userId
                        )
                        """)
                .setParameter("userId", userId)
                .executeUpdate();

        entityManager.createQuery("delete from LoginSessionEntity session where session.userId = :userId")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private LoginSession session(WalletLogin login) {
        return loginSessionReader.findById(claims(login).sessionId()).orElseThrow();
    }

    private AccessTokenClaims claims(WalletLogin login) {
        return accessTokenValidator.readClaims(login.accessToken().value());
    }

    private void assertSessionAndRefreshToken(WalletLogin login, User user, Instant loggedInAt) {
        Instant sessionExpiresAt = loggedInAt.plus(authTokenProperties.sessionTtl());
        AccessTokenClaims claims = claims(login);
        LoginSession session = loginSessionReader.findById(claims.sessionId()).orElseThrow();

        assertThat(claims.userId()).isEqualTo(user.id());
        assertThat(session.userId()).isEqualTo(user.id());
        assertThat(session.expiresAt()).isEqualTo(sessionExpiresAt);
        assertThat(session.revokedAt()).isNull();

        assertThat(login.accessToken().expiresAt()).isEqualTo(
                loggedInAt.truncatedTo(ChronoUnit.SECONDS).plus(authTokenProperties.accessTokenTtl()));
        assertThat(login.refreshToken().expiresAt()).isEqualTo(sessionExpiresAt);

        RefreshToken storedToken = refreshTokenReader
                .findByRefreshTokenHash(refreshTokenHasher.hash(login.refreshToken().value())).orElseThrow();

        assertThat(storedToken.sessionId()).isEqualTo(session.id());
        assertThat(storedToken.expiresAt()).isEqualTo(sessionExpiresAt);
        assertThat(storedToken.usedAt()).isNull();
    }

    private void assertError(ErrorType expected, String address, String signature) {
        assertThatThrownBy(() -> service.login(address, signature))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private void assertUnusedAndUnregistered(WalletAuthMessage expected) {
        assertThat(messageReader.findByWalletAddress(expected.walletAddress())).contains(expected);
        assertThat(expected.usedAt()).isNull();
        assertThat(userReader.findByWalletAddress(expected.walletAddress())).isEmpty();
    }

    private LedgerAccountEntity account(UserId userId) {
        return accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(userId.value())).orElseThrow();
    }

    private ECKeyPair newWallet() {
        BigInteger testPrivateKey = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16)
                .add(BigInteger.ONE);
        ECKeyPair wallet = ECKeyPair.create(testPrivateKey);
        walletAddresses.add(address(wallet));

        return wallet;
    }

    private String address(ECKeyPair wallet) {
        return "0x" + Keys.getAddress(wallet.getPublicKey());
    }

    private String sign(WalletAuthMessage message, ECKeyPair wallet) {
        Sign.SignatureData data = Sign.signPrefixedMessage(message.message().getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();

        return "0x" + hex.formatHex(data.getR()) + hex.formatHex(data.getS()) + hex.formatHex(data.getV());
    }
}
