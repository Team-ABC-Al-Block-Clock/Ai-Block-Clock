package com.aiblockclock.support.blockchain;

import java.math.BigInteger;
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
    static final String TOKEN_NAME = "ABC Payment Token";
    static final String TOKEN_SYMBOL = "abcToken";
    static final String PAYMENT_PARTITION = "payment";
    static final BigInteger TOKEN_GRANULARITY = BigInteger.ONE;

    private final SsafyStoClient ssafyStoClient;
    private final SsafyTokenDeploymentProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        String ownerAddress = properties.ownerAddress();
        SsafyTokenDeploymentRequest request = new SsafyTokenDeploymentRequest(
                ownerAddress,
                TOKEN_NAME,
                TOKEN_SYMBOL,
                TOKEN_GRANULARITY,
                List.of(ownerAddress),
                List.of(PAYMENT_PARTITION)
        );

        SsafyTokenDeploymentReceipt receipt = ssafyStoClient.deployToken(request);
        if (!Boolean.TRUE.equals(receipt.status())) {
            throw new IllegalStateException("SSAFY token deployment transaction failed: " + receipt.transactionHash());
        }

        log.info("SSAFY token deployment succeeded. contractAddress={}, transactionHash={}",
                receipt.contractAddress(), receipt.transactionHash());
    }
}
