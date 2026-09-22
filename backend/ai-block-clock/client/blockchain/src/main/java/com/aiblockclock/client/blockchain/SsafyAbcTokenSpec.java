package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

final class SsafyAbcTokenSpec {
    static final String NAME = "ABC Payment Token";
    static final String SYMBOL = "abcToken";
    static final String PAYMENT_PARTITION = "payment";
    static final BigInteger GRANULARITY = BigInteger.ONE;

    private SsafyAbcTokenSpec() {
    }
}
