package com.aiblockclock.client.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyTokenMintReceipt(
        Boolean status,
        String transactionHash
) {
}
