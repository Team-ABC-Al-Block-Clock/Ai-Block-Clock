package com.aiblockclock.support.blockchain;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto.deployment")
public record SsafyTokenDeploymentProperties(
        String ownerAddress,
        String confirmation
) {
    static final String REQUIRED_CONFIRMATION = "deploy-abc-token";

    public SsafyTokenDeploymentProperties {
        SsafyBlockchainAddress.requireValid(ownerAddress, "ssafy.sto.deployment.owner-address");
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("ssafy.sto.deployment.confirmation must match the required deployment phrase");
        }
    }
}
