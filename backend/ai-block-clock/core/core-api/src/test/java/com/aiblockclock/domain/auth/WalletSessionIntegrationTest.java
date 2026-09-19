package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.LoginSessionEntity;
import com.aiblockclock.storage.auth.LoginSessionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("integration")
@SpringBootTest
@Transactional
class WalletSessionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-09-17T00:00:00.123456Z");

    @Autowired
    private WalletAuthService service;

    @Autowired
    private SessionTokenHasher hasher;

    @Autowired
    private LoginSessionRepository sessionRepository;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void 서버_시각을_고정한다() {
        when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void 로그인하면_원문은_사용자에게_주고_DB에는_해시와_7일_만료_시각만_저장한다() {
        ECKeyPair wallet = newWallet();

        WalletLogin login = login(wallet);
        String rawToken = login.sessionToken().value();
        String tokenHash = hasher.hash(rawToken);
        LoginSessionEntity persisted = sessionRepository.findBySessionTokenHash(tokenHash).orElseThrow();

        assertThat(rawToken).matches("[0-9a-f]{64}");
        assertThat(persisted.getSessionTokenHash()).isEqualTo(tokenHash).isNotEqualTo(rawToken);
        assertThat(persisted.getExpiresAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 60 * 60));
        assertThat(service.authenticate(rawToken).userId()).isEqualTo(login.user().id());
    }

    @Test
    void 같은_사용자가_다시_로그인하면_이전_세션은_종료되고_새_세션만_사용할_수_있다() {
        ECKeyPair wallet = newWallet();
        WalletLogin first = login(wallet);

        WalletLogin second = login(wallet);

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(second.sessionToken().value()).isNotEqualTo(first.sessionToken().value());
        assertError(ErrorType.LOGIN_SESSION_REVOKED,
                () -> service.authenticate(first.sessionToken().value()));
        assertThat(service.authenticate(second.sessionToken().value()).userId()).isEqualTo(second.user().id());
    }

    @Test
    void 로그아웃하거나_7일이_지나면_세션을_사용할_수_없다() {
        WalletLogin loggedOut = login(newWallet());
        service.logout(loggedOut.sessionToken().value());
        assertError(ErrorType.LOGIN_SESSION_REVOKED,
                () -> service.authenticate(loggedOut.sessionToken().value()));

        WalletLogin expired = login(newWallet());
        when(clock.instant()).thenReturn(expired.sessionToken().expiresAt());
        assertError(ErrorType.LOGIN_SESSION_EXPIRED,
                () -> service.authenticate(expired.sessionToken().value()));
    }

    private WalletLogin login(ECKeyPair wallet) {
        WalletAuthMessage message = service.issueMessage(address(wallet));
        return service.login(address(wallet), sign(message, wallet));
    }

    private void assertError(ErrorType expected, Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(CoreException.class,
                exception -> assertThat(exception.getErrorType()).isEqualTo(expected));
    }

    private ECKeyPair newWallet() {
        return ECKeyPair.create(new BigInteger(
                UUID.randomUUID().toString().replace("-", ""), 16
        ).add(BigInteger.ONE));
    }

    private String address(ECKeyPair wallet) {
        return "0x" + Keys.getAddress(wallet.getPublicKey());
    }

    private String sign(WalletAuthMessage message, ECKeyPair wallet) {
        Sign.SignatureData signed = Sign.signPrefixedMessage(
                message.message().getBytes(StandardCharsets.UTF_8), wallet
        );
        HexFormat hex = HexFormat.of();
        return "0x" + hex.formatHex(signed.getR())
                + hex.formatHex(signed.getS()) + hex.formatHex(signed.getV());
    }
}
