package com.aiblockclock.client.blockchain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto.token")
public record SsafyTokenProperties(String contractAddress) {
    public SsafyTokenProperties {
        if (contractAddress != null && !contractAddress.isBlank()
                && !SsafyBlockchainAddress.isValid(contractAddress)) {
            throw new IllegalArgumentException(
                    "ssafy.sto.token.contract-address must be a 0x-prefixed 40-hex blockchain address"
            );
        }
    }

    public String requiredContractAddress() {
        return SsafyBlockchainAddress.requireValid(contractAddress, "ssafy.sto.token.contract-address");
    }
}
