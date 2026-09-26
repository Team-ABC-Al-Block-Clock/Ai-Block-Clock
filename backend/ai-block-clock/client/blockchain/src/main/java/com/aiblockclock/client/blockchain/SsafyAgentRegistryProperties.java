package com.aiblockclock.client.blockchain;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 에이전트 원본 기록 전용 토큰(등기부)과, 그 토큰에 문서를 쓸 때 서명하는 등기부 지갑의 개인키다.
// 셋이 모두 비어 있으면 기록을 쓰지 않는 서버로 기동한다. 하나라도 채웠으면 셋 다 맞아야 기동한다.
@ConfigurationProperties(prefix = "ssafy.sto.agent-registry")
public record SsafyAgentRegistryProperties(
        String contractAddress,
        String signerPrivateKey,
        Duration writeTimeout
) {
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]{64}$");

    public SsafyAgentRegistryProperties {
        contractAddress = contractAddress == null ? "" : contractAddress.strip();
        signerPrivateKey = signerPrivateKey == null ? "" : signerPrivateKey.strip();

        if (!contractAddress.isEmpty() || !signerPrivateKey.isEmpty()) {
            validate(contractAddress, signerPrivateKey, writeTimeout);
        }
    }

    public boolean isConfigured() {
        return !contractAddress.isEmpty();
    }

    // 개인키가 로그 · 오류 메시지에 찍히지 않게 가린다
    @Override
    public String toString() {
        return "SsafyAgentRegistryProperties[contractAddress=" + contractAddress
                + ", signerPrivateKey=" + (signerPrivateKey.isEmpty() ? "" : "****")
                + ", writeTimeout=" + writeTimeout + "]";
    }

    private static void validate(String contractAddress, String signerPrivateKey, Duration writeTimeout) {
        if (!SsafyBlockchainAddress.isValid(contractAddress)) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.sto.agent-registry.contract-address must be a 0x-prefixed 40-hex blockchain address");
        }

        if (!PRIVATE_KEY_PATTERN.matcher(signerPrivateKey).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.sto.agent-registry.signer-private-key must be 64 hex characters");
        }

        // 토큰증권 API 는 거래가 블록에 적힐 때까지 기다렸다가 답한다. 블록이 약 10초마다 생겨 조회보다 길게 둔다
        if (writeTimeout == null || writeTimeout.compareTo(Duration.ofSeconds(1)) < 0
                || writeTimeout.compareTo(Duration.ofMillis(Integer.MAX_VALUE)) > 0) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.sto.agent-registry.write-timeout must be between 1s and " + Integer.MAX_VALUE + "ms");
        }
    }
}
