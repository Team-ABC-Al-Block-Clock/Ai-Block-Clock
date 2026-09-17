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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    private final WalletAuthMessageGenerator generator = mock(WalletAuthMessageGenerator.class);
    private final WalletAuthMessageReader reader = mock(WalletAuthMessageReader.class);
    private final WalletAuthMessageWriter writer = mock(WalletAuthMessageWriter.class);
    private final WalletSignatureValidator validator = spy(new WalletSignatureValidator());
    private final UserReader userReader = mock(UserReader.class);
    private final WalletLoginManager loginManager = mock(WalletLoginManager.class);
    private final AccessTokenValidator accessTokenValidator = mock(AccessTokenValidator.class);
    private final LoginSessionReader loginSessionReader = mock(LoginSessionReader.class);
    private final WalletAuthService service = new WalletAuthService(generator, reader, writer, validator,
            userReader, loginManager, accessTokenValidator, loginSessionReader, Clock.fixed(NOW, ZoneOffset.UTC));

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
    void 기존_사용자는_메시지를_사용_처리한_뒤_반환한다() {
        User existing = user();
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(MESSAGE));
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(existing));

        assertThat(service.login(ADDRESS, signature())).isSameAs(existing);
        verify(writer).modifyAsUsed(MESSAGE);
        verifyNoInteractions(loginManager);
    }

    @Test
    void 신규_사용자의_메시지_사용_처리는_Manager_트랜잭션에_맡긴다() {
        User registered = user();
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(MESSAGE));
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.empty());
        when(loginManager.register(MESSAGE)).thenReturn(registered);

        assertThat(service.login(ADDRESS, signature())).isSameAs(registered);
        verifyNoInteractions(writer);
        verify(loginManager).register(MESSAGE);
    }

    @Test
    void 기존_사용자여도_메시지_사용_처리에_실패하면_로그인_결과를_반환하지_않는다() {
        when(reader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(MESSAGE));
        when(userReader.findByWalletAddress(ADDRESS)).thenReturn(Optional.of(user()));
        doThrow(new CoreException(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE)).when(writer).modifyAsUsed(MESSAGE);

        assertError(ErrorType.WALLET_AUTH_MESSAGE_NOT_USABLE, signature());
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

    private void assertError(ErrorType expected, String signature) {
        assertThatThrownBy(() -> service.login(ADDRESS, signature))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(expected));
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
