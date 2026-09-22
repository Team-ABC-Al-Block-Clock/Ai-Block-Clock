package com.aiblockclock.client.blockchain;

import java.util.regex.Pattern;

final class SsafyBlockchainAddress {
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private SsafyBlockchainAddress() {
    }

    static String requireValid(String address, String parameterName) {
        if (!isValid(address)) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_ADDRESS,
                    parameterName + " must be a 0x-prefixed 40-hex blockchain address");
        }

        return address;
    }

    static boolean isValid(String address) {
        return address != null && ADDRESS_PATTERN.matcher(address).matches();
    }
}
