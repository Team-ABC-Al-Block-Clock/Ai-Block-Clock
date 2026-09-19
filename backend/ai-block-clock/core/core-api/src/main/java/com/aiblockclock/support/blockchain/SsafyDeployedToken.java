package com.aiblockclock.support.blockchain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyDeployedToken(
        String name,
        String symbol,
        String contractAddress,
        String txId,
        Instant deployedAt
) {
}
