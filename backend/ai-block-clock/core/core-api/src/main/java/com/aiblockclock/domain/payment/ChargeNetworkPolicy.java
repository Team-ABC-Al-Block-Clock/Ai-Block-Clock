package com.aiblockclock.domain.payment;

public record ChargeNetworkPolicy(
        Long chainId,
        String custodyAddress,
        String usdcContractAddress,
        long requiredConfirmations
) {
}
