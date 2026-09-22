package com.aiblockclock.storage.auth;

import jakarta.persistence.EntityManager;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class WalletAuthMessagePersistenceIntegrationTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-09-16T12:00:00Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(300);

    @Autowired
    private EntityManager entityManager;

    @Test
    void 가입하지_않은_지갑의_메시지_원문과_발급_정보를_저장한다() {
        String walletAddress = "0x7777777777777777777777777777777777777777";
        String nonce = "a".repeat(64);
        String message = """
                j15a403.p.ssafy.io wants you to sign in with your Ethereum account:
                %s

                Sign in to AI Block Clock.

                URI: https://j15a403.p.ssafy.io/
                Version: 1
                Chain ID: 80002
                Nonce: %s
                Issued At: %s
                Expiration Time: %s""".formatted(walletAddress, nonce, ISSUED_AT, EXPIRES_AT);
        Long userCount = entityManager.createQuery(
                "select count(user) from UserEntity user where user.walletAddress = :walletAddress",
                Long.class
        ).setParameter("walletAddress", walletAddress).getSingleResult();

        assertThat(userCount).isZero();
        assertThat(message.length()).isGreaterThan(255);

        WalletAuthMessageEntity entity = WalletAuthMessageEntity.of(
                walletAddress, nonce, message, ISSUED_AT, EXPIRES_AT
        );
        entityManager.persist(entity);
        entityManager.flush();
        entityManager.clear();

        WalletAuthMessageEntity found = entityManager.find(WalletAuthMessageEntity.class, entity.getId());

        assertThat(found.getId()).isPositive();
        assertThat(found.getWalletAddress()).isEqualTo(walletAddress);
        assertThat(found.getNonce()).isEqualTo(nonce);
        assertThat(found.getMessage()).isEqualTo(message);
        assertThat(found.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(found.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.getUsedAt()).isNull();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void 같은_지갑의_발급_기록을_여러_행으로_저장할_수_없다() {
        String walletAddress = "0x8888888888888888888888888888888888888888";
        entityManager.persist(WalletAuthMessageEntity.of(
                walletAddress, "b".repeat(64), "first message", ISSUED_AT, EXPIRES_AT
        ));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(WalletAuthMessageEntity.of(
                    walletAddress, "c".repeat(64), "second message", ISSUED_AT, EXPIRES_AT
            ));
            entityManager.flush();
        }).isInstanceOfSatisfying(ConstraintViolationException.class, exception ->
                assertThat(exception.getConstraintName())
                        .containsIgnoringCase("uq_wallet_auth_message_wallet_address"));
    }
}
