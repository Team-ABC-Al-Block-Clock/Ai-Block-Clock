package com.aiblockclock.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

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

    public boolean isWaitingForConfirmation() {
        return status == OnChainTransactionStatus.NOT_FOUND
                || status == OnChainTransactionStatus.PENDING;
    }

    public boolean isFailed() {
        return status == OnChainTransactionStatus.FAILED;
    }

    public boolean matchesChainId(Long expectedChainId) {
        return Objects.equals(chainId, expectedChainId);
    }

    public boolean matchesTransactionHash(
            String expectedTransactionHash
    ) {
        return matchesHexValue(
                transactionHash,
                expectedTransactionHash
        );
    }

    public boolean matchesSenderWalletAddress(
            String expectedSenderWalletAddress
    ) {
        return matchesHexValue(
                senderWalletAddress,
                expectedSenderWalletAddress
        );
    }

    public boolean matchesRecipientAddress(
            String expectedRecipientAddress
    ) {
        return matchesHexValue(
                recipientAddress,
                expectedRecipientAddress
        );
    }

    public boolean matchesTokenContractAddress(
            String expectedTokenContractAddress
    ) {
        return matchesHexValue(
                tokenContractAddress,
                expectedTokenContractAddress
        );
    }

    public boolean matchesAmount(DepositAmount expectedAmount) {
        return amount != null
                && expectedAmount != null
                && amount.compareTo(expectedAmount.value()) == 0;
    }

    public boolean hasRequiredConfirmations(
            long requiredConfirmations
    ) {
        return confirmations >= requiredConfirmations;
    }

    private boolean matchesHexValue(
            String actual,
            String expected
    ) {
        return actual != null
                && expected != null
                && actual.equalsIgnoreCase(expected);
    }
}
