package com.aiblockclock.client.blockchain;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// SSAFY 체인 노드의 JSON-RPC 주소다. 토큰증권 API(ssafy.sto)와 다른 창구라 설정을 나눈다.
// 토큰증권 API 에는 거래 해시로 영수증을 읽는 기능이 없어서, 입금 확인은 이 창구로 한다.
@ConfigurationProperties(prefix = "ssafy.chain")
public record SsafyChainProperties(
        URI rpcUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public SsafyChainProperties {
        if (rpcUrl == null || rpcUrl.getHost() == null
                || !("https".equalsIgnoreCase(rpcUrl.getScheme())
                || "http".equalsIgnoreCase(rpcUrl.getScheme()))
                || rpcUrl.getUserInfo() != null || rpcUrl.getQuery() != null
                || rpcUrl.getFragment() != null) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.chain.rpc-url must be an HTTP(S) server URI without credentials, query or fragment");
        }

        validateTimeout(connectTimeout, "ssafy.chain.connect-timeout");
        validateTimeout(readTimeout, "ssafy.chain.read-timeout");
    }

    private static void validateTimeout(Duration timeout, String propertyName) {
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    propertyName + " must be between 1ms and " + Integer.MAX_VALUE + "ms");
        }
    }
}
