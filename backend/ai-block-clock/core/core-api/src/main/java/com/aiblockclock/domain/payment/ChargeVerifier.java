package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatusReason;

import java.math.BigDecimal;
import java.util.Objects;

public final class ChargeVerifier {

    public ChargeVerificationResult verify(
            ChargeVerificationCommand command,
            OnChainUsdcTransfer transfer
    ) {
        return switch (transfer.status()) {
            case NOT_FOUND, PENDING ->
                    ChargeVerificationResult.waiting();

            case FAILED ->
                    ChargeVerificationResult.rejected(
                            ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED
                    );

            case SUCCESS ->
                    verifySuccessful(command, transfer);
        };
    }

    private ChargeVerificationResult verifySuccessful(
            ChargeVerificationCommand command,
            OnChainUsdcTransfer transfer
    ) {
        if (!Objects.equals(
                command.expectedChainId(),
                transfer.chainId()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.CHAIN_ID_MISMATCH
            );
        }

        if (!sameHexValue(
                command.expectedTransactionHash(),
                transfer.transactionHash()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.TRANSACTION_HASH_MISMATCH
            );
        }

        if (!sameHexValue(
                command.expectedSenderWalletAddress(),
                transfer.senderWalletAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.SENDER_WALLET_MISMATCH
            );
        }

        if (!sameHexValue(
                command.expectedCustodyAddress(),
                transfer.recipientAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.CUSTODY_ADDRESS_MISMATCH
            );
        }

        if (!sameHexValue(
                command.expectedUsdcContractAddress(),
                transfer.tokenContractAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.USDC_CONTRACT_MISMATCH
            );
        }

        if (!sameAmount(
                command.expectedUsdcAmount(),
                transfer.amount()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.AMOUNT_MISMATCH
            );
        }

        if (transfer.confirmations()
                < command.requiredConfirmations()) {
            return ChargeVerificationResult.waiting();
        }

        return ChargeVerificationResult.verified();
    }

    private boolean sameHexValue(
            String expected,
            String actual
    ) {
        return expected != null
                && actual != null
                && expected.equalsIgnoreCase(actual);
    }

    private boolean sameAmount(
            BigDecimal expected,
            BigDecimal actual
    ) {
        return expected != null
                && actual != null
                && expected.compareTo(actual) == 0;
    }
}
