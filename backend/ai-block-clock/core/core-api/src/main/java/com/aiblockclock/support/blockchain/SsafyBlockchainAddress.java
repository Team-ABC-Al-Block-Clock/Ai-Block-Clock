package com.aiblockclock.support.blockchain;

import java.util.regex.Pattern;

final class SsafyBlockchainAddress {
    private static final Pattern ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private SsafyBlockchainAddress() {
    }

    static String requireValid(String address, String propertyName) {
        if (address == null || !ADDRESS_PATTERN.matcher(address).matches()) {
            throw new IllegalArgumentException(propertyName + " must be a 0x-prefixed 40-hex blockchain address");
        }
        return address;
    }

    static boolean isValid(String address) {
        return address != null && ADDRESS_PATTERN.matcher(address).matches();
    }
}
