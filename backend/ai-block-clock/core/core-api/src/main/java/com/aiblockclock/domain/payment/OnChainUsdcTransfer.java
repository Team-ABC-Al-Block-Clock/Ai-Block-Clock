package com.aiblockclock.domain.payment;

import java.math.BigDecimal;

public record OnChainUsdcTransfer(
        OnChainTransactionStatus status,
        Long chainId,
        String transactionHash,
        String senderWalletAddress,
        String recipientAddress,
        String tokenContractAddress,
        BigDecimal amount,
        long confirmations
) {
}
