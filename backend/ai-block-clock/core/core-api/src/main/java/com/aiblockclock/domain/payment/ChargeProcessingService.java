package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatusReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargeProcessingService {

    private final ChargeRequestReader chargeRequestReader;
    private final OnChainChargeVerifier onChainChargeVerifier;
    private final AbcConversionPolicy abcConversionPolicy;
    private final ChargeRequestWriter chargeRequestWriter;
    private final ChargeConfirmationManager chargeConfirmationManager;

    public ChargeRequest process(
            Long chargeRequestId,
            OnChainUsdcTransfer onChainUsdcTransfer,
            ChargeNetworkPolicy chargeNetworkPolicy
    ) {
        ChargeRequest chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        if (!chargeRequest.isPending()) {
            return chargeRequest;
        }

        if (!chargeNetworkPolicy.supportsChain(
                chargeRequest.getChainId()
        )) {
            return rejectCharge(
                    chargeRequest,
                    ChargeStatusReason.CHAIN_ID_MISMATCH
            );
        }

        ChargeVerificationCriteria verificationCriteria =
                chargeNetworkPolicy.createVerificationCriteria(
                        chargeRequest
                );

        ChargeVerificationResult verificationResult =
                onChainChargeVerifier.verify(
                        verificationCriteria,
                        onChainUsdcTransfer
                );

        return switch (verificationResult.outcome()) {
            case WAITING -> chargeRequest;
            case REJECTED -> rejectCharge(
                    chargeRequest,
                    verificationResult.reason()
            );
            case VERIFIED -> confirmCharge(chargeRequest);
        };
    }

    private ChargeRequest rejectCharge(
            ChargeRequest chargeRequest,
            ChargeStatusReason failureReason
    ) {
        chargeRequest.fail(failureReason);
        return chargeRequestWriter.updateFromPending(chargeRequest);
    }

    private ChargeRequest confirmCharge(
            ChargeRequest chargeRequest
    ) {
        AbcAmount creditedAbcAmount =
                abcConversionPolicy.convert(
                        chargeRequest.getRequestedDepositAmount()
                );

        return chargeConfirmationManager.confirm(
                chargeRequest,
                creditedAbcAmount
        );
    }
}
