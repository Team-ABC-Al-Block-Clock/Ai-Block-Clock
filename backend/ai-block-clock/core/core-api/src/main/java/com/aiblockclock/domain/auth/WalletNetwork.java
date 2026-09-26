package com.aiblockclock.domain.auth;

import java.util.Arrays;
import java.util.Optional;

// 체인 ID 와 네트워크 이름은 짝이 맞아야 하므로 한곳에서 묶는다.
// 설정(wallet-auth.chain-id)이 여기 없는 체인을 가리키면 서버가 뜨지 않는다.
// 로그인과 충전이 같은 체인을 쓴다. 사용자가 MetaMask 네트워크를 한 번만 고르면 된다.
public enum WalletNetwork {
    SSAFY(31221);

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
