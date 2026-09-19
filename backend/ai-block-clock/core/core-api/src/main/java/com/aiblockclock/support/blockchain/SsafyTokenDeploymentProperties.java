package com.aiblockclock.support.blockchain;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ssafy.sto.deployment")
public record SsafyTokenDeploymentProperties(
        String ownerAddress,
        String confirmation
) {
    static final String REQUIRED_CONFIRMATION = "deploy-abc-token";
    private static final Pattern WALLET_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    public SsafyTokenDeploymentProperties {
        if (ownerAddress == null || !WALLET_ADDRESS_PATTERN.matcher(ownerAddress).matches()) {
            throw new IllegalArgumentException("ssafy.sto.deployment.owner-address must be a 0x-prefixed 40-hex wallet address");
        }
        if (!REQUIRED_CONFIRMATION.equals(confirmation)) {
            throw new IllegalArgumentException("ssafy.sto.deployment.confirmation must match the required deployment phrase");
        }
    }
}
