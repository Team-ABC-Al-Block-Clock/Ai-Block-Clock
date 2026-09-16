package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WalletNonceGeneratorTest {

    private final WalletNonceGenerator walletNonceGenerator = new WalletNonceGenerator();

    @Test
    @DisplayName("nonce는 64글자의 소문자 16진수로 생성한다")
    void generateLowercaseHexNonce() {
        String nonce = walletNonceGenerator.generate();

        assertThat(nonce).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("반복 발급한 nonce는 같은 값을 재사용하지 않는다")
    void generateFreshNonceOnEachCall() {
        List<String> nonces = new ArrayList<>();

        for (int index = 0; index < 100; index++) {
            nonces.add(walletNonceGenerator.generate());
        }

        assertThat(nonces).doesNotHaveDuplicates();
    }
}
