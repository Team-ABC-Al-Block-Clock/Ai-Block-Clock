package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletAuthMessageGeneratorTest {

    private static final String NONCE = "a".repeat(64);
    private static final String CHECKSUM_ADDRESS = "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359";
    private static final Instant NOW = Instant.parse("2026-09-16T12:00:00.123456789Z");

    private final WalletNonceGenerator walletNonceGenerator = mock(WalletNonceGenerator.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("체크섬 주소와 발급 정보를 SIWE 메시지에 정확히 넣는다")
    void generateSiweMessage() {
        when(walletNonceGenerator.generate()).thenReturn(NONCE);

        WalletAuthMessageGenerator generator = new WalletAuthMessageGenerator(
                new WalletAuthProperties("j15a403.p.ssafy.io",
                        URI.create("https://j15a403.p.ssafy.io/"), 80002L, Duration.ofMinutes(5)),
                walletNonceGenerator,
                clock
        );

        WalletAuthMessage result = generator.generate("0xfb6916095ca1df60bb79ce92ce3ea74c37c5d359");

        assertThat(result.nonce()).isEqualTo(NONCE);
        assertThat(result.issuedAt()).isEqualTo(Instant.parse("2026-09-16T12:00:00.123456Z"));
        assertThat(result.expiresAt()).isEqualTo(Instant.parse("2026-09-16T12:05:00.123456Z"));
        assertThat(result.message()).isEqualTo(String.join("\n",
                "https://j15a403.p.ssafy.io wants you to sign in with your Ethereum account:",
                CHECKSUM_ADDRESS,
                "",
                "Sign in to ABC.",
                "",
                "URI: https://j15a403.p.ssafy.io/",
                "Version: 1",
                "Chain ID: 80002",
                "Nonce: " + NONCE,
                "Issued At: 2026-09-16T12:00:00.123456Z",
                "Expiration Time: 2026-09-16T12:05:00.123456Z"
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359",
            "0xFB6916095CA1DF60BB79CE92CE3EA74C37C5D359"
    })
    @DisplayName("주소의 대소문자가 달라도 같은 저장 주소와 체크섬 표기를 만든다")
    void normalizeAddress(String walletAddress) {
        when(walletNonceGenerator.generate()).thenReturn(NONCE);

        WalletAuthMessageGenerator generator = new WalletAuthMessageGenerator(
                new WalletAuthProperties("j15a403.p.ssafy.io",
                        URI.create("https://j15a403.p.ssafy.io/"), 80002L, Duration.ofMinutes(5)),
                walletNonceGenerator,
                clock
        );

        WalletAuthMessage result = generator.generate(walletAddress);

        assertThat(result.walletAddress()).isEqualTo("0xfb6916095ca1df60bb79ce92ce3ea74c37c5d359");
        assertThat(result.message().split("\n")[1]).isEqualTo(CHECKSUM_ADDRESS);
    }

    @Test
    @DisplayName("로컬 HTTP 주소와 설정된 유효 시간을 사용한다")
    void useLocalOriginAndConfiguredTtl() {
        when(walletNonceGenerator.generate()).thenReturn(NONCE);

        WalletAuthMessageGenerator generator = new WalletAuthMessageGenerator(
                new WalletAuthProperties("localhost:5173", URI.create("http://localhost:5173/"),
                        80002L, Duration.ofSeconds(90)),
                walletNonceGenerator,
                clock
        );

        WalletAuthMessage result = generator.generate(CHECKSUM_ADDRESS);

        assertThat(result.message())
                .startsWith("http://localhost:5173 wants you to sign in with your Ethereum account:")
                .contains("URI: http://localhost:5173/")
                .endsWith("Expiration Time: 2026-09-16T12:01:30.123456Z");
        assertThat(Duration.between(result.issuedAt(), result.expiresAt())).isEqualTo(Duration.ofSeconds(90));
    }
}
