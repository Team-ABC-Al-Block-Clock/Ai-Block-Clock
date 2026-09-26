package com.aiblockclock.client.blockchain;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 서버가 거래에 서명할 때 쓰는 지갑의 개인키다. 출금 지급에서 보관 지갑이 보내는 전송에만 쓴다.
// 저장소 · 이미지에 넣지 않고 서버의 환경 변수로만 받는다. 비우면 서명하지 않는다(지급을 켜지 않은 환경).
@ConfigurationProperties(prefix = "ssafy.chain.signer")
public record SsafyChainSignerProperties(String privateKey) {
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]{64}$");

    public SsafyChainSignerProperties {
        privateKey = privateKey == null ? "" : privateKey.strip();

        if (!privateKey.isEmpty() && !PRIVATE_KEY_PATTERN.matcher(privateKey).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.chain.signer.private-key must be 64 hex characters");
        }
    }

    public boolean isConfigured() {
        return !privateKey.isEmpty();
    }

    // 설정을 로그 · 오류로 찍어도 키가 새지 않게 한다
    @Override
    public String toString() {
        return "SsafyChainSignerProperties[privateKey=" + (isConfigured() ? "****" : "") + "]";
    }
}
