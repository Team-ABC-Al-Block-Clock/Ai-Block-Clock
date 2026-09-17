package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
class WalletLogoutIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00.123456Z");

    @Autowired
    private WalletAuthService service;

    @Autowired
    private LoginSessionReader sessionReader;

    @Autowired
    private RefreshTokenReader tokenReader;

    @Autowired
    private RefreshTokenHasher hasher;

    @Autowired
    private AccessTokenValidator accessTokenValidator;

    @Autowired
    private UserReader userReader;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LedgerAccountRepository accountRepository;

    @Autowired
    private WalletAuthMessageRepository messageRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    @MockitoSpyBean
    private LoginSessionRepository sessionRepository;

    private final List<String> walletAddresses = new ArrayList<>();

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void 테스트에서_만든_지갑에_연결된_데이터만_정리한다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            for (String address : walletAddresses) {
                userRepository.findByWalletAddressIgnoreCase(address).ifPresent(user -> {
                    jdbcTemplate.update("delete from refresh_token where session_id in "
                            + "(select id from login_session where user_id = ?)", user.getId());
                    jdbcTemplate.update("delete from login_session where user_id = ?", user.getId());
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
    void 로그아웃은_세션을_종료하고_사용자와_ABC_계정과_토큰_기록은_유지한다() {
        WalletLogin login = login(newWallet());
        LoginSession originalSession = session(login);
        RefreshToken originalToken = token(login.refreshToken().value());
        LedgerAccountEntity originalAccount = account(login);
        User originalUser = userReader.findByWalletAddress(login.user().walletAddress()).orElseThrow();

        service.logout(login.refreshToken().value());

        LoginSession ended = session(login);
        assertThat(ended.revokedAt()).isEqualTo(NOW);
        assertThat(ended.expiresAt()).isEqualTo(originalSession.expiresAt());
        assertThat(ended.userId()).isEqualTo(login.user().id());
        assertThat(userReader.findByWalletAddress(login.user().walletAddress())).contains(originalUser);
        assertThat(account(login).getId()).isEqualTo(originalAccount.getId());
        assertThat(account(login).getBalance()).isEqualByComparingTo(originalAccount.getBalance());
        assertThat(account(login).getVersion()).isEqualTo(originalAccount.getVersion());
        assertThat(token(login.refreshToken().value())).isEqualTo(originalToken);
    }

    @Test
    void 로그아웃한_세션의_Access_Token_요청과_Refresh_Token_재발급을_차단한다() {
        WalletLogin login = login(newWallet());

        service.logout(login.refreshToken().value());

        assertAuthenticationError(ErrorType.LOGIN_SESSION_REVOKED, login.accessToken().value());
        assertThatThrownBy(() -> service.reissue(login.refreshToken().value()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_REVOKED));
    }

    @Test
    void Access_Token이_만료돼도_유효한_Refresh_Token으로_로그아웃한다() {
        WalletLogin login = login(newWallet());
        LoginSessionId sessionId = claims(login).sessionId();
        Instant loggedOutAt = NOW.plusSeconds(901);
        when(clock.instant()).thenReturn(loggedOutAt);

        assertAuthenticationError(ErrorType.ACCESS_TOKEN_EXPIRED, login.accessToken().value());
        service.logout(login.refreshToken().value());

        assertThat(sessionReader.findById(sessionId).orElseThrow().revokedAt())
                .isEqualTo(loggedOutAt);
    }

    @Test
    void 같은_토큰으로_로그아웃을_반복해도_최초_종료_시각과_버전을_유지한다() {
        WalletLogin login = login(newWallet());
        service.logout(login.refreshToken().value());
        LoginSession firstLogout = session(login);
        Long firstVersion = sessionRepository.findById(firstLogout.id().value()).orElseThrow().getVersion();
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        service.logout(login.refreshToken().value());

        assertThat(session(login)).isEqualTo(firstLogout);
        assertThat(sessionRepository.findById(firstLogout.id().value()).orElseThrow().getVersion())
                .isEqualTo(firstVersion);
    }

    @Test
    void 발급하지_않은_Refresh_Token은_거절하고_기존_세션을_유지한다() {
        WalletLogin login = login(newWallet());

        assertLogoutError(ErrorType.REFRESH_TOKEN_NOT_USABLE, "unissued-refresh-token");

        assertThat(session(login).revokedAt()).isNull();
        assertThat(service.authenticate(login.accessToken().value()).userId()).isEqualTo(login.user().id());
    }

    @Test
    void 만료된_Refresh_Token으로는_세션을_변경하지_않는다() {
        WalletLogin login = login(newWallet());
        LoginSessionId sessionId = claims(login).sessionId();
        when(clock.instant()).thenReturn(login.refreshToken().expiresAt());

        assertLogoutError(ErrorType.REFRESH_TOKEN_EXPIRED, login.refreshToken().value());

        assertThat(sessionReader.findById(sessionId).orElseThrow().revokedAt()).isNull();
    }

    @Test
    void 재발급에_사용한_토큰은_로그아웃에도_사용하지_못하고_새_토큰으로_종료한다() {
        WalletLogin login = login(newWallet());
        AuthTokens reissued = service.reissue(login.refreshToken().value());

        assertLogoutError(ErrorType.REFRESH_TOKEN_ALREADY_USED, login.refreshToken().value());
        assertThat(service.authenticate(reissued.accessToken().value()).userId()).isEqualTo(login.user().id());
        service.logout(reissued.refreshToken().value());

        assertAuthenticationError(ErrorType.LOGIN_SESSION_REVOKED, reissued.accessToken().value());
    }

    @Test
    void 다른_사용자의_로그인_세션은_종료하지_않는다() {
        WalletLogin first = login(newWallet());
        WalletLogin second = login(newWallet());

        service.logout(first.refreshToken().value());

        assertThat(session(first).revokedAt()).isEqualTo(NOW);
        assertThat(session(second).revokedAt()).isNull();
        assertThat(service.authenticate(second.accessToken().value()).userId()).isEqualTo(second.user().id());
    }

    @Test
    void 이전_로그인의_토큰으로_종료해도_새_로그인_세션은_유지한다() {
        ECKeyPair wallet = newWallet();
        WalletLogin previous = login(wallet);
        WalletLogin current = login(wallet);
        LoginSession previousSession = session(previous);

        service.logout(previous.refreshToken().value());

        assertThat(session(previous)).isEqualTo(previousSession);
        assertThat(session(current).revokedAt()).isNull();
        assertThat(service.authenticate(current.accessToken().value()).id()).isEqualTo(session(current).id());
    }

    @Test
    void DB_종료_변경_후_오류가_발생하면_롤백하고_오류를_전달한다() {
        WalletLogin login = login(newWallet());
        LoginSession originalSession = session(login);
        Long originalVersion = sessionRepository.findById(originalSession.id().value()).orElseThrow().getVersion();
        Answer<?> repositoryAnswer = mockingDetails(sessionRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            repositoryAnswer.answer(invocation);

            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }).when(sessionRepository).updateRevokedAtIfNotRevoked(anyLong(), any(Instant.class));

        assertLogoutError(ErrorType.INTERNAL_ERROR, login.refreshToken().value());

        assertThat(session(login)).isEqualTo(originalSession);
        assertThat(sessionRepository.findById(originalSession.id().value()).orElseThrow().getVersion())
                .isEqualTo(originalVersion);
        assertThat(service.authenticate(login.accessToken().value()).id()).isEqualTo(originalSession.id());
    }

    private WalletLogin login(ECKeyPair wallet) {
        String address = address(wallet);
        WalletAuthMessage message = service.issueMessage(address);
        Sign.SignatureData signature = Sign.signPrefixedMessage(
                message.message().getBytes(StandardCharsets.UTF_8), wallet);
        HexFormat hex = HexFormat.of();
        String rawSignature = "0x" + hex.formatHex(signature.getR())
                + hex.formatHex(signature.getS()) + hex.formatHex(signature.getV());

        return service.login(address, rawSignature);
    }

    private LoginSession session(WalletLogin login) {
        return sessionReader.findById(claims(login).sessionId()).orElseThrow();
    }

    private AccessTokenClaims claims(WalletLogin login) {
        return accessTokenValidator.readClaims(login.accessToken().value());
    }

    private RefreshToken token(String rawRefreshToken) {
        return tokenReader.findByRefreshTokenHash(hasher.hash(rawRefreshToken)).orElseThrow();
    }

    private LedgerAccountEntity account(WalletLogin login) {
        return accountRepository.findByAccountTypeAndReferenceId(
                LedgerAccountType.ABC_BALANCE, Long.toString(login.user().id().value())
        ).orElseThrow();
    }

    private void assertLogoutError(ErrorType expected, String rawRefreshToken) {
        assertThatThrownBy(() -> service.logout(rawRefreshToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private void assertAuthenticationError(ErrorType expected, String rawAccessToken) {
        assertThatThrownBy(() -> service.authenticate(rawAccessToken))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private ECKeyPair newWallet() {
        BigInteger privateKey = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16)
                .add(BigInteger.ONE);
        ECKeyPair wallet = ECKeyPair.create(privateKey);
        walletAddresses.add(address(wallet));

        return wallet;
    }

    private String address(ECKeyPair wallet) {
        return "0x" + Keys.getAddress(wallet.getPublicKey());
    }
}
