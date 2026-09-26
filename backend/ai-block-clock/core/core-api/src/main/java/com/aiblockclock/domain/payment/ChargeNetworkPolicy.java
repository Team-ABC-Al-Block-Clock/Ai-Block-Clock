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

    // 보관 지갑이 스스로에게 보내면 토큰은 제자리인데 ABC 만 늘어난다
    public boolean isCustody(String walletAddress) {
        return custodyAddress != null && custodyAddress.equalsIgnoreCase(walletAddress);
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
