package com.aiblockclock.domain.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WalletNetworkTest {
    @Test
    void 체인_ID_80002는_폴리곤_아모이다() {
        assertThat(WalletNetwork.findByChainId(80002)).contains(WalletNetwork.POLYGON_AMOY);
    }

    @Test
    void 목록에_없는_체인_ID는_찾지_못한다() {
        assertThat(WalletNetwork.findByChainId(137)).isEmpty();
    }
}
