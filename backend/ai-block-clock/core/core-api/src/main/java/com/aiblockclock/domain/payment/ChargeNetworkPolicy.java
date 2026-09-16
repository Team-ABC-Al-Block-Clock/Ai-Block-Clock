package com.aiblockclock.domain.payment;

public record ChargeNetworkPolicy(
        Long chainId,
        String custodyAddress,
        String depositTokenContractAddress,
        long requiredConfirmations
) {

    public boolean supportsChain(Long requestedChainId) {
        return chainId != null
                && chainId.equals(requestedChainId);
    }

    public ChargeVerificationCriteria createVerificationCriteria(
            ChargeRequest chargeRequest
    ) {
        return new ChargeVerificationCriteria(
                chainId,
                chargeRequest.getTransactionHash(),
                chargeRequest.getSenderWalletAddress(),
                chargeRequest.getRequestedDepositAmount(),
                custodyAddress,
                depositTokenContractAddress,
                requiredConfirmations
        );
    }
}
