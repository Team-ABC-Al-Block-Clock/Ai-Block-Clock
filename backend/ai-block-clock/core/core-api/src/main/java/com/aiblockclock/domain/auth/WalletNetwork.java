package com.aiblockclock.domain.auth;

import java.util.Arrays;
import java.util.Optional;

// 체인 ID 와 네트워크 이름은 짝이 맞아야 하므로 한곳에서 묶는다.
// 설정(wallet-auth.chain-id)이 여기 없는 체인을 가리키면 서버가 뜨지 않는다.
public enum WalletNetwork {
    POLYGON_AMOY(80002);

    private final long chainId;

    WalletNetwork(long chainId) {
        this.chainId = chainId;
    }

    public static Optional<WalletNetwork> findByChainId(long chainId) {
        return Arrays.stream(values())
                .filter(network -> network.chainId == chainId)
                .findFirst();
    }

    public long chainId() {
        return chainId;
    }
}
