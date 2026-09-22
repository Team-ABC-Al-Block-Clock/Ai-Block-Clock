package com.aiblockclock.domain.auth;

import com.aiblockclock.storage.auth.WalletAuthMessageEntity;
import com.aiblockclock.storage.auth.WalletAuthMessageRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class WalletAuthMessageReaderIntegrationTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-09-16T12:00:00.123456Z");
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(300);
    private static final String NONCE = "d".repeat(64);
    private static final String MESSAGE = "ABC wallet sign-in message\n".repeat(12) + "Nonce: " + NONCE;

    @Autowired
    private WalletAuthMessageRepository walletAuthMessageRepository;

    @Autowired
    private WalletAuthMessageReader walletAuthMessageReader;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 지갑_주소로_원문과_발급_정보를_도메인_객체로_조회한다() {
        String walletAddress = "0x9999999999999999999999999999999999999999";
        walletAuthMessageRepository.saveAndFlush(WalletAuthMessageEntity.of(
                walletAddress, NONCE, MESSAGE, ISSUED_AT, EXPIRES_AT
        ));
        entityManager.clear();

        WalletAuthMessage found = walletAuthMessageReader.findByWalletAddress(walletAddress).orElseThrow();

        assertThat(found.walletAddress()).isEqualTo(walletAddress);
        assertThat(found.nonce()).isEqualTo(NONCE);
        assertThat(found.message()).isEqualTo(MESSAGE);
        assertThat(found.issuedAt()).isEqualTo(ISSUED_AT);
        assertThat(found.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(found.usedAt()).isNull();
    }

    @Test
    void 발급_기록이_없는_지갑을_조회하면_빈_결과를_반환한다() {
        assertThat(walletAuthMessageReader.findByWalletAddress(
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        )).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            "0xFB6916095CA1DF60BB79CE92CE3EA74C37C5D359"
    })
    void 주소의_대소문자가_달라도_소문자로_저장된_기록을_조회한다(String walletAddress) {
        String storedAddress = walletAddress.toLowerCase(Locale.ROOT);
        walletAuthMessageRepository.saveAndFlush(WalletAuthMessageEntity.of(
                storedAddress, NONCE, MESSAGE, ISSUED_AT, EXPIRES_AT
        ));
        entityManager.clear();

        WalletAuthMessage found = walletAuthMessageReader.findByWalletAddress(walletAddress).orElseThrow();

        assertThat(found.walletAddress()).isEqualTo(storedAddress);
        assertThat(found.message()).isEqualTo(MESSAGE);
    }

    @Test
    void 사용된_기록도_사용_시간을_포함해서_반환한다() {
        String walletAddress = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        Instant usedAt = ISSUED_AT.plusSeconds(30);
        WalletAuthMessageEntity saved = walletAuthMessageRepository.saveAndFlush(WalletAuthMessageEntity.of(
                walletAddress, NONCE, MESSAGE, ISSUED_AT, EXPIRES_AT
        ));
        entityManager.createQuery("""
                update WalletAuthMessageEntity authMessage
                set authMessage.usedAt = :usedAt
                where authMessage.id = :id
                """)
                .setParameter("usedAt", usedAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.clear();

        WalletAuthMessage found = walletAuthMessageReader.findByWalletAddress(walletAddress).orElseThrow();

        assertThat(found.usedAt()).isEqualTo(usedAt);
        assertThat(found.message()).isEqualTo(MESSAGE);
    }
}
