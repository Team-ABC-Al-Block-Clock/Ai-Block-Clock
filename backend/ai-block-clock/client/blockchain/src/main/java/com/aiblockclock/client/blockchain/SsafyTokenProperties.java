package com.aiblockclock.client.blockchain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto.token")
public record SsafyTokenProperties(String contractAddress) {
    private static final String CONTRACT_ADDRESS_MESSAGE =
            "ssafy.sto.token.contract-address must be a 0x-prefixed 40-hex blockchain address";

    // 컨트랙트 주소가 없어도 애플리케이션은 기동한다. 형식이 틀린 값만 기동 시 거부한다.
    public SsafyTokenProperties {
        if (contractAddress != null && !contractAddress.isBlank()
                && !SsafyBlockchainAddress.isValid(contractAddress)) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    CONTRACT_ADDRESS_MESSAGE);
        }
    }

    public String requiredContractAddress() {
        if (!SsafyBlockchainAddress.isValid(contractAddress)) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    CONTRACT_ADDRESS_MESSAGE);
        }

        return contractAddress;
    }
}
