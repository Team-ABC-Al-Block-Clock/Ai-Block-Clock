package com.aiblockclock.domain.payment;

import java.math.BigDecimal;

public record ChargeVerificationCommand(
        Long expectedChainId,
        String expectedTransactionHash,
        String expectedSenderWalletAddress,
        BigDecimal expectedUsdcAmount,
        String expectedCustodyAddress,
        String expectedUsdcContractAddress,
        long requiredConfirmations
) {
}
