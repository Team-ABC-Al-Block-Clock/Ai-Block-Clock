package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatusReason;
import org.springframework.stereotype.Component;

@Component
public final class OnChainChargeVerifier {

    public ChargeVerificationResult verify(
            ChargeVerificationCriteria criteria,
            OnChainTokenTransfer onChainTransfer
    ) {
        if (onChainTransfer.isUnknown()) {
            return ChargeVerificationResult.unreachable();
        }

        if (onChainTransfer.isWaitingForConfirmation()) {
            return ChargeVerificationResult.waiting();
        }

        if (onChainTransfer.isFailed()) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.ONCHAIN_TRANSACTION_FAILED
            );
        }

        return verifySuccessful(criteria, onChainTransfer);
    }

    private ChargeVerificationResult verifySuccessful(
            ChargeVerificationCriteria criteria,
            OnChainTokenTransfer onChainTransfer
    ) {
        if (!onChainTransfer.matchesChainId(
                criteria.expectedChainId()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.CHAIN_ID_MISMATCH
            );
        }

        if (!onChainTransfer.matchesTransactionHash(
                criteria.expectedTransactionHash()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.TRANSACTION_HASH_MISMATCH
            );
        }

        if (!onChainTransfer.matchesSenderWalletAddress(
                criteria.expectedSenderWalletAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.SENDER_WALLET_MISMATCH
            );
        }

        if (!onChainTransfer.matchesRecipientAddress(
                criteria.expectedCustodyAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.CUSTODY_ADDRESS_MISMATCH
            );
        }

        if (!onChainTransfer.matchesTokenContractAddress(
                criteria.expectedDepositTokenContractAddress()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.DEPOSIT_TOKEN_CONTRACT_MISMATCH
            );
        }

        if (!onChainTransfer.matchesAmount(
                criteria.expectedDepositAmount()
        )) {
            return ChargeVerificationResult.rejected(
                    ChargeStatusReason.AMOUNT_MISMATCH
            );
        }

        if (!onChainTransfer.hasRequiredConfirmations(
                criteria.requiredConfirmations()
        )) {
            return ChargeVerificationResult.waiting();
        }

        return ChargeVerificationResult.verified();
    }
}
