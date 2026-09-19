package com.aiblockclock.support.blockchain;

import java.math.BigInteger;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"requestor", "to", "value", "data", "signature"})
public record SsafyTokenMintRequest(
        String requestor,
        String to,
        BigInteger value,
        String data,
        String signature
) {
    private static final Pattern SIGNATURE_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{130}$");

    public SsafyTokenMintRequest {
        SsafyBlockchainAddress.requireValid(requestor, "requestor");
        SsafyBlockchainAddress.requireValid(to, "to");
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("value must be a positive integer");
        }
        if (data == null || data.isBlank()) {
            throw new IllegalArgumentException("data must not be blank");
        }
        if (signature == null || !SIGNATURE_PATTERN.matcher(signature).matches()) {
            throw new IllegalArgumentException("signature must be a 0x-prefixed 65-byte hexadecimal signature");
        }
    }
}
