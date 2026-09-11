package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestEntity;

public record ChargeNetworkPolicy(
        Long chainId,
        String custodyAddress,
        String usdcContractAddress,
        long requiredConfirmations
) {

    public boolean supportsChain(Long requestedChainId) {
        return chainId != null
                && chainId.equals(requestedChainId);
    }

    public ChargeVerificationCommand createVerificationCommand(
            ChargeRequestEntity chargeRequest
    ) {
        return new ChargeVerificationCommand(
                chainId,
                chargeRequest.getTransactionHash(),
                chargeRequest.getSenderWalletAddress(),
                chargeRequest.getRequestedUsdcAmount(),
                custodyAddress,
                usdcContractAddress,
                requiredConfirmations
        );
    }
}
