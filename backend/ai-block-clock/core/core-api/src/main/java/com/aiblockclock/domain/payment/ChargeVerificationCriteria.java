package com.aiblockclock.domain.payment;

public record ChargeVerificationCriteria(
        Long expectedChainId,
        String expectedTransactionHash,
        String expectedSenderWalletAddress,
        DepositAmount expectedDepositAmount,
        String expectedCustodyAddress,
        String expectedDepositTokenContractAddress,
        long requiredConfirmations
) {
}
