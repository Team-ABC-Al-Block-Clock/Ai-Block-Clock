package com.aiblockclock.support.blockchain;

import java.math.BigInteger;
import java.util.List;

public record SsafyTokenDeploymentRequest(
        String requestor,
        String name,
        String symbol,
        BigInteger granularity,
        List<String> controllers,
        List<String> partitions
) {
    public SsafyTokenDeploymentRequest {
        controllers = List.copyOf(controllers);
        partitions = List.copyOf(partitions);
    }
}
