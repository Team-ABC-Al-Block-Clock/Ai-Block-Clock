package com.aiblockclock.domain.auth;

import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.user.UserReader;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WalletAuthServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:01:00Z");
    private static final ECKeyPair TEST_KEY_PAIR = ECKeyPair.create(BigInteger.ONE);
    private static final String ADDRESS = "0x" + Keys.getAddress(TEST_KEY_PAIR.getPublicKey());
    private static final WalletAuthMessage MESSAGE = new WalletAuthMessage(ADDRESS, "a".repeat(64),
            "Hello World", NOW.minusSeconds(60), NOW.plusSeconds(240), null);
    private static final Duration SESSION_TTL = Duration.ofDays(7);
    private static final Instant SESSION_EXPIRES_AT = NOW.plus(SESSION_TTL);
    private static final String RAW_REFRESH_TOKEN = "b".repeat(64);
    private static final String REFRESH_TOKEN_HASH = "c".repeat(64);
    private static final AccessToken ACCESS_TOKEN = new AccessToken("issued-jwt", NOW.plusSeconds(900));

    private final WalletAuthMessageGenerator generator = mock(WalletAuthMessageGenerator.class);
    private final WalletAuthMessageReader reader = mock(WalletAuthMessageReader.class);
    private final WalletAuthMessageWriter writer = mock(WalletAuthMessageWriter.class);
    private final WalletSignatureValidator validator = spy(new WalletSignatureValidator());
    private final UserReader userReader = mock(UserReader.class);
    private final WalletLoginManager loginManager = mock(WalletLoginManager.class);
    private final RefreshTokenGenerator refreshTokenGenerator = mock(RefreshTokenGenerator.class);
    private final RefreshTokenHasher refreshTokenHasher = mock(RefreshTokenHasher.class);
    private final RefreshTokenReader refreshTokenReader = mock(RefreshTokenReader.class);
    private final TokenReissueManager tokenReissueManager = mock(TokenReissueManager.class);
    private final AccessTokenValidator accessTokenValidator = mock(AccessTokenValidator.class);
    private final LoginSessionReader loginSessionReader = mock(LoginSessionReader.class);
    private final LoginSessionWriter loginSessionWriter = mock(LoginSessionWriter.class);
    private final AuthTokenProperties properties = new AuthTokenProperties(
            "ai-block-clock", Duration.ofMinutes(15), SESSION_TTL, "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    private final WalletAuthService service = new WalletAuthService(generator, reader, writer, validator,
            userReader, loginManager, refreshTokenGenerator, refreshTokenHasher, refreshTokenReader, tokenReissueManager,
            accessTokenValidator, loginSessionReader, loginSessionWriter, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 메시지_발급은_생성한_메시지를_저장하고_저장_결과를_반환한다() {
        when(generator.generate(ADDRESS)).thenReturn(MESSAGE);
        when(writer.append(MESSAGE)).thenReturn(MESSAGE);

        assertThat(service.issueMessage(ADDRESS)).isSameAs(MESSAGE);
        verify(writer).append(MESSAGE);
        verifyNoInteractions(userReader, loginManager, validator);
    }

    @Test
    void 메시지를_발급하지_않고_로그인하면_거절한다() {
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.empty());

        assertError(ErrorType.WALLET_AUTH_MESSAGE_NOT_FOUND, signature());
        verifyNoInteractions(validator, userReader, writer, loginManager);
    }

    @Test
    void 만료된_메시지는_서명_검증과_사용자_처리_전에_거절한다() {
        WalletAuthMessage expired = new WalletAuthMessage(ADDRESS, MESSAGE.nonce(), MESSAGE.message(),
                NOW.minusSeconds(300), NOW, null);
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(expired));

        assertError(ErrorType.WALLET_AUTH_MESSAGE_EXPIRED, signature());
        verifyNoInteractions(validator, userReader, writer, loginManager);
    }

    @Test
    void 사용한_메시지는_서명_검증과_사용자_처리_전에_거절한다() {
        WalletAuthMessage used = new WalletAuthMessage(ADDRESS, MESSAGE.nonce(), MESSAGE.message(),
                MESSAGE.issuedAt(), MESSAGE.expiresAt(), NOW.minusSeconds(30));
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(used));

        assertError(ErrorType.WALLET_AUTH_MESSAGE_ALREADY_USED, signature());
        verifyNoInteractions(validator, userReader, writer, loginManager);
    }

    @Test
    void 잘못된_서명이면_사용자_조회나_메시지_사용_처리를_하지_않는다() {
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(MESSAGE));

        assertError(ErrorType.WALLET_SIGNATURE_INVALID, "0x123");
        verifyNoInteractions(userReader, writer, loginManager);
    }

    @Test
    void 기존_사용자는_Manager로_세션을_만들고_토큰_두_개를_함께_반환한다() {
        User existing = user();
        LoginSession session = session();
        givenIssuedRefreshToken();
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(existing));
        when(loginManager.login(MESSAGE, existing, REFRESH_TOKEN_HASH, SESSION_EXPIRES_AT))
                .thenReturn(new WalletLoginSession(existing, session, ACCESS_TOKEN));

        WalletLogin login = service.login(ADDRESS, signature());

        assertThat(login.user()).isSameAs(existing);
        assertThat(login.accessToken()).isSameAs(ACCESS_TOKEN);
        assertThat(login.refreshToken()).isEqualTo(new IssuedRefreshToken(RAW_REFRESH_TOKEN, SESSION_EXPIRES_AT));
        verify(loginManager).login(MESSAGE, existing, REFRESH_TOKEN_HASH, SESSION_EXPIRES_AT);
        verifyNoInteractions(writer);
    }

    @Test
    void 신규_사용자는_Manager의_가입_트랜잭션에서_세션까지_만든다() {
        User registered = user();
        LoginSession session = session();
        givenIssuedRefreshToken();
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.empty());
        when(loginManager.register(MESSAGE, REFRESH_TOKEN_HASH, SESSION_EXPIRES_AT))
                .thenReturn(new WalletLoginSession(registered, session, ACCESS_TOKEN));

        WalletLogin login = service.login(ADDRESS, signature());

        assertThat(login.user()).isSameAs(registered);
        assertThat(login.accessToken()).isSameAs(ACCESS_TOKEN);
        verify(loginManager).register(MESSAGE, REFRESH_TOKEN_HASH, SESSION_EXPIRES_AT);
        verifyNoInteractions(writer);
    }

    @Test
    void 저장에_넘기는_값은_Refresh_Token_원문이_아니라_해시다() {
        givenIssuedRefreshToken();
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.empty());
        when(loginManager.register(MESSAGE, REFRESH_TOKEN_HASH, SESSION_EXPIRES_AT))
                .thenReturn(new WalletLoginSession(user(), session(), ACCESS_TOKEN));

        WalletLogin login = service.login(ADDRESS, signature());

        assertThat(login.refreshToken().value()).isEqualTo(RAW_REFRESH_TOKEN);
        verify(refreshTokenHasher).hash(RAW_REFRESH_TOKEN);
        verify(loginManager).register(eq(MESSAGE), eq(REFRESH_TOKEN_HASH), any(Instant.class));
    }

    @Test
    void Manager의_저장이_실패하면_로그인_결과를_반환하지_않는다() {
        givenIssuedRefreshToken();
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(user()));
        doThrow(new CoreException(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE))
                .when(loginManager).login(eq(MESSAGE), any(User.class), eq(REFRESH_TOKEN_HASH), any(Instant.class));

        assertError(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE, signature());
        verifyNoInteractions(writer);
    }

    @Test
    void 요청_인증은_토큰과_일치하는_유효한_세션을_반환한다() {
        AccessTokenClaims claims = new AccessTokenClaims(UserId.of(123L), LoginSessionId.of(100L));
        LoginSession session = new LoginSession(claims.sessionId(), claims.userId(), NOW.plusSeconds(60), null);
        when(accessTokenValidator.readClaims("test-token")).thenReturn(claims);
        when(loginSessionReader.findById(claims.sessionId())).thenReturn(Optional.of(session));

        assertThat(service.authenticate("test-token")).isSameAs(session);
        verify(loginSessionReader).findById(claims.sessionId());
        verifyNoInteractions(generator, reader, writer, validator, userReader, loginManager);
    }

    @Test
    void 토큰_검증에_실패하면_DB_세션을_조회하지_않는다() {
        when(accessTokenValidator.readClaims("invalid-token"))
                .thenThrow(new CoreException(ErrorType.ACCESS_TOKEN_INVALID));

        assertThatThrownBy(() -> service.authenticate("invalid-token"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ACCESS_TOKEN_INVALID));
        verifyNoInteractions(loginSessionReader);
    }

    @Test
    void 토큰의_세션이_DB에_없으면_요청_인증을_거절한다() {
        AccessTokenClaims claims = new AccessTokenClaims(UserId.of(123L), LoginSessionId.of(100L));
        when(accessTokenValidator.readClaims("test-token")).thenReturn(claims);
        when(loginSessionReader.findById(claims.sessionId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("test-token"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.LOGIN_SESSION_NOT_USABLE));
    }

    @Test
    void 토큰의_사용자와_DB_세션의_사용자가_다르면_거절한다() {
        AccessTokenClaims claims = new AccessTokenClaims(UserId.of(123L), LoginSessionId.of(100L));
        LoginSession session = new LoginSession(claims.sessionId(), UserId.of(456L), NOW.plusSeconds(60), null);
        when(accessTokenValidator.readClaims("test-token")).thenReturn(claims);
        when(loginSessionReader.findById(claims.sessionId())).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.authenticate("test-token"))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ACCESS_TOKEN_INVALID));
    }

    @Test
    void 재발급은_기존_토큰을_해시로_찾고_새_원문과_Access_Token을_반환한다() {
        RefreshToken stored = givenRefreshToken();
        when(refreshTokenGenerator.generate()).thenReturn("new-refresh-token");
        when(refreshTokenHasher.hash("new-refresh-token")).thenReturn("new-refresh-token-hash");
        when(tokenReissueManager.reissue(stored, "new-refresh-token-hash")).thenReturn(ACCESS_TOKEN);

        AuthTokens tokens = service.reissue(RAW_REFRESH_TOKEN);

        assertThat(tokens.accessToken()).isSameAs(ACCESS_TOKEN);
        assertThat(tokens.refreshToken()).isEqualTo(new IssuedRefreshToken("new-refresh-token", SESSION_EXPIRES_AT));
        verify(tokenReissueManager).reissue(stored, "new-refresh-token-hash");
        verifyNoInteractions(userReader, loginManager, writer, accessTokenValidator);
    }

    @Test
    void 저장되지_않은_Refresh_Token은_재발급하지_않는다() {
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());

        assertReissueError(ErrorType.REFRESH_TOKEN_NOT_USABLE);
        verifyNoInteractions(loginSessionReader, refreshTokenGenerator, tokenReissueManager);
    }

    @Test
    void 사용한_Refresh_Token은_세션_조회_전에_재발급을_거절한다() {
        givenRefreshToken();
        RefreshToken used = new RefreshToken(RefreshTokenId.of(500L), session().id(), REFRESH_TOKEN_HASH,
                SESSION_EXPIRES_AT, NOW.minusSeconds(1));
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(used));

        assertReissueError(ErrorType.REFRESH_TOKEN_ALREADY_USED);
        verifyNoInteractions(loginSessionReader, refreshTokenGenerator, tokenReissueManager);
    }

    @Test
    void 연결된_세션이_없으면_새_토큰을_만들지_않는다() {
        givenRefreshToken();
        when(loginSessionReader.findById(session().id())).thenReturn(Optional.empty());

        assertReissueError(ErrorType.LOGIN_SESSION_NOT_USABLE);
        verifyNoInteractions(refreshTokenGenerator, tokenReissueManager);
    }

    @Test
    void 재발급_Manager가_실패하면_새_토큰을_반환하지_않는다() {
        RefreshToken stored = givenRefreshToken();
        when(refreshTokenGenerator.generate()).thenReturn("new-refresh-token");
        when(refreshTokenHasher.hash("new-refresh-token")).thenReturn("new-refresh-token-hash");
        when(tokenReissueManager.reissue(stored, "new-refresh-token-hash"))
                .thenThrow(new CoreException(ErrorType.REFRESH_TOKEN_NOT_USABLE));

        assertReissueError(ErrorType.REFRESH_TOKEN_NOT_USABLE);
    }

    @Test
    void 로그아웃은_Refresh_Token으로_찾은_세션만_종료한다() {
        RefreshToken stored = givenRefreshToken();

        service.logout(RAW_REFRESH_TOKEN);

        verify(loginSessionWriter).modifyAsRevoked(stored.sessionId());
        verifyNoInteractions(accessTokenValidator, loginSessionReader, refreshTokenGenerator,
                tokenReissueManager, userReader, loginManager, writer);
    }

    @Test
    void 저장되지_않은_Refresh_Token으로는_세션을_종료하지_않는다() {
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.empty());

        assertLogoutError(ErrorType.REFRESH_TOKEN_NOT_USABLE);
        verifyNoInteractions(loginSessionWriter);
    }

    @Test
    void 사용한_Refresh_Token으로는_로그아웃하지_않는다() {
        givenRefreshToken();
        RefreshToken used = new RefreshToken(RefreshTokenId.of(500L), session().id(), REFRESH_TOKEN_HASH,
                SESSION_EXPIRES_AT, NOW.minusSeconds(1));
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(used));

        assertLogoutError(ErrorType.REFRESH_TOKEN_ALREADY_USED);
        verifyNoInteractions(loginSessionWriter);
    }

    @Test
    void 만료된_Refresh_Token으로는_로그아웃하지_않는다() {
        givenRefreshToken();
        RefreshToken expired = new RefreshToken(RefreshTokenId.of(500L), session().id(), REFRESH_TOKEN_HASH,
                NOW, null);
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(expired));

        assertLogoutError(ErrorType.REFRESH_TOKEN_EXPIRED);
        verifyNoInteractions(loginSessionWriter);
    }

    @Test
    void 세션_종료가_실패하면_로그아웃_오류를_그대로_전달한다() {
        RefreshToken stored = givenRefreshToken();
        doThrow(new CoreException(ErrorType.INTERNAL_ERROR))
                .when(loginSessionWriter).modifyAsRevoked(stored.sessionId());

        assertLogoutError(ErrorType.INTERNAL_ERROR);
    }

    private void assertLogoutError(ErrorType expected) {
        assertThatThrownBy(() -> service.logout(RAW_REFRESH_TOKEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private RefreshToken givenRefreshToken() {
        RefreshToken stored = new RefreshToken(RefreshTokenId.of(500L), session().id(), REFRESH_TOKEN_HASH,
                SESSION_EXPIRES_AT, null);
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
        when(refreshTokenReader.findByRefreshTokenHash(REFRESH_TOKEN_HASH)).thenReturn(Optional.of(stored));
        when(loginSessionReader.findById(stored.sessionId())).thenReturn(Optional.of(session()));

        return stored;
    }

    private void assertReissueError(ErrorType expected) {
        assertThatThrownBy(() -> service.reissue(RAW_REFRESH_TOKEN))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private void assertError(ErrorType expected, String signature) {
        assertThatThrownBy(() -> service.login(ADDRESS, signature))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private void givenIssuedRefreshToken() {
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(MESSAGE));
        when(refreshTokenGenerator.generate()).thenReturn(RAW_REFRESH_TOKEN);
        when(refreshTokenHasher.hash(RAW_REFRESH_TOKEN)).thenReturn(REFRESH_TOKEN_HASH);
    }

    private LoginSession session() {
        return new LoginSession(LoginSessionId.of(100L), UserId.of(123L), SESSION_EXPIRES_AT, null);
    }

    private User user() {
        return new User(UserId.of(123L), ADDRESS, 0L, NOW.minusSeconds(600), NOW.minusSeconds(600));
    }

    private String signature() {
        Sign.SignatureData data = Sign.signPrefixedMessage(MESSAGE.message().getBytes(StandardCharsets.UTF_8),
                TEST_KEY_PAIR);
        HexFormat hex = HexFormat.of();

        return "0x" + hex.formatHex(data.getR()) + hex.formatHex(data.getS()) + hex.formatHex(data.getV());
    }
}
