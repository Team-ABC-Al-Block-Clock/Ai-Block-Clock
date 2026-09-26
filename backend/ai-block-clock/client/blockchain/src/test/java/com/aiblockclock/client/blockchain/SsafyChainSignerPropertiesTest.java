package com.aiblockclock.client.blockchain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsafyChainSignerPropertiesTest {
    private static final String KEY = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    @Test
    void 비어_있으면_서명하지_않는_설정이다() {
        assertThat(new SsafyChainSignerProperties(null).isConfigured()).isFalse();
        assertThat(new SsafyChainSignerProperties("  ").isConfigured()).isFalse();
    }

    @Test
    void 앞의_0x_는_있어도_없어도_된다() {
        assertThat(new SsafyChainSignerProperties(KEY).isConfigured()).isTrue();
        assertThat(new SsafyChainSignerProperties("0x" + KEY).isConfigured()).isTrue();
    }

    @Test
    void 길이가_틀린_키는_기동하지_않는다() {
        assertThatThrownBy(() -> new SsafyChainSignerProperties(KEY.substring(1)))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_CONFIGURATION));
    }

    @Test
    void 설정을_글로_찍어도_키가_나오지_않는다() {
        assertThat(new SsafyChainSignerProperties(KEY).toString()).doesNotContain(KEY).contains("****");
    }
}
