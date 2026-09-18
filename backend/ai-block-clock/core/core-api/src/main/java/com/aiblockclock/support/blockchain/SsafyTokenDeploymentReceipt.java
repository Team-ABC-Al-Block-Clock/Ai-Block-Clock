package com.aiblockclock.support.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyTokenDeploymentReceipt(
        Boolean status,
        String transactionHash,
        String contractAddress
) {
}
