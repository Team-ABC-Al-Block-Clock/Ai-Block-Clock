package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto.mint")
public record SsafyTokenMintProperties(
        String requestorAddress,
        String recipientAddress,
        BigInteger value,
        String data,
        String signature,
        String confirmation
) {
    static final String REQUIRED_CONFIRMATION = "mint-abc-test-token";

    public SsafyTokenMintProperties {
        SsafyBlockchainAddress.requireValid(requestorAddress, "ssafy.sto.mint.requestor-address");
        SsafyBlockchainAddress.requireValid(recipientAddress, "ssafy.sto.mint.recipient-address");
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("ssafy.sto.mint.value must be a positive integer");
        }
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("ssafy.sto.mint.data must not be blank");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("ssafy.sto.mint.signature must not be blank");
        }
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("ssafy.sto.mint.confirmation must match the required mint phrase");
        }
    }
}
