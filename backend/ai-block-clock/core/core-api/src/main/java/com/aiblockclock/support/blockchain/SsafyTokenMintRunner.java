package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("token-mint")
@RequiredArgsConstructor
@EnableConfigurationProperties(SsafyTokenMintProperties.class)
public class SsafyTokenMintRunner implements ApplicationRunner {
    private final SsafyStoClient ssafyStoClient;
    private final SsafyTokenProperties tokenProperties;
    private final SsafyTokenMintProperties mintProperties;

    @Override
    public void run(ApplicationArguments args) {
        String contractAddress = tokenProperties.requiredContractAddress();
        verifyAbcToken(contractAddress);
        verifyMintPermission(contractAddress);

        BigInteger balanceBefore = ssafyStoClient.getBalance(
                contractAddress, mintProperties.recipientAddress());
        if (balanceBefore.signum() != 0) {
            throw new IllegalStateException(
                    "Test recipient already has abcToken; refusing a possibly repeated test mint"
            );
        }
        BigInteger totalSupplyBefore = ssafyStoClient.getTotalSupply(contractAddress);

        SsafyTokenMintRequest request = new SsafyTokenMintRequest(
                mintProperties.requestorAddress(),
                mintProperties.recipientAddress(),
                mintProperties.value(),
                mintProperties.data(),
                mintProperties.signature()
        );
        SsafyTokenMintReceipt receipt = ssafyStoClient.mintToken(contractAddress, request);
        if (!Boolean.TRUE.equals(receipt.status())) {
            throw new IllegalStateException(
                    "SSAFY token mint transaction failed: " + receipt.transactionHash()
            );
        }

        BigInteger balanceAfter = ssafyStoClient.getBalance(
                contractAddress, mintProperties.recipientAddress());
        BigInteger totalSupplyAfter = ssafyStoClient.getTotalSupply(contractAddress);
        verifyMintedAmount("recipient balance", balanceBefore, balanceAfter);
        verifyMintedAmount("total supply", totalSupplyBefore, totalSupplyAfter);

        log.info(
                "SSAFY token mint succeeded. recipient={}, value={}, transactionHash={}, balanceAfter={}, totalSupplyAfter={}",
                mintProperties.recipientAddress(), mintProperties.value(), receipt.transactionHash(),
                balanceAfter, totalSupplyAfter
        );
    }

    private void verifyAbcToken(String contractAddress) {
        String name = ssafyStoClient.getTokenName(contractAddress);
        String symbol = ssafyStoClient.getTokenSymbol(contractAddress);
        BigInteger granularity = ssafyStoClient.getGranularity(contractAddress);

        if (!SsafyAbcTokenSpec.NAME.equals(name)
                || !SsafyAbcTokenSpec.SYMBOL.equals(symbol)
                || !SsafyAbcTokenSpec.GRANULARITY.equals(granularity)) {
            throw new IllegalStateException("Configured contract does not match the deployed ABC token specification");
        }
        if (!mintProperties.value().mod(granularity).equals(BigInteger.ZERO)) {
            throw new IllegalStateException("Mint value must be a multiple of the token granularity");
        }
    }

    private void verifyMintPermission(String contractAddress) {
        if (!ssafyStoClient.isMinter(contractAddress, mintProperties.requestorAddress())) {
            throw new IllegalStateException("Configured requestor does not have the minter role");
        }
        if (!ssafyStoClient.isIssuable(contractAddress)) {
            throw new IllegalStateException("ABC token issuance is disabled");
        }
    }

    private void verifyMintedAmount(String target, BigInteger before, BigInteger after) {
        BigInteger expected = before.add(mintProperties.value());
        if (!expected.equals(after)) {
            throw new IllegalStateException(
                    "SSAFY token mint completed but " + target
                            + " did not increase by the requested value; verify transaction "
                            + "before any new mint attempt"
            );
        }
    }
}
