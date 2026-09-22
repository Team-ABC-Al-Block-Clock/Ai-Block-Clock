package com.aiblockclock.client.blockchain;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("token-deployment")
@RequiredArgsConstructor
@EnableConfigurationProperties(SsafyTokenDeploymentProperties.class)
public class SsafyTokenDeploymentRunner implements ApplicationRunner {
    private final SsafyStoClient ssafyStoClient;
    private final SsafyTokenDeploymentProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String ownerAddress = properties.ownerAddress();
        SsafyDeployedToken existingToken = ssafyStoClient.getDeployedTokens(ownerAddress).stream()
                .filter(this::matchesAbcToken)
                .findFirst()
                .orElse(null);
        if (existingToken != null) {
            throw new IllegalStateException("ABC token is already deployed: " + existingToken.contractAddress());
        }

        SsafyTokenDeploymentRequest request = new SsafyTokenDeploymentRequest(
                ownerAddress,
                SsafyAbcTokenSpec.NAME,
                SsafyAbcTokenSpec.SYMBOL,
                SsafyAbcTokenSpec.GRANULARITY,
                List.of(ownerAddress),
                List.of(SsafyAbcTokenSpec.PAYMENT_PARTITION)
        );

        SsafyTokenDeploymentReceipt receipt = ssafyStoClient.deployToken(request);
        if (!Boolean.TRUE.equals(receipt.status())) {
            throw new IllegalStateException("SSAFY token deployment transaction failed: " + receipt.transactionHash());
        }

        log.info("SSAFY token deployment succeeded. contractAddress={}, transactionHash={}",
                receipt.contractAddress(), receipt.transactionHash());
    }

    private boolean matchesAbcToken(SsafyDeployedToken token) {
        return SsafyAbcTokenSpec.NAME.equalsIgnoreCase(token.name())
                || SsafyAbcTokenSpec.SYMBOL.equalsIgnoreCase(token.symbol());
    }
}
