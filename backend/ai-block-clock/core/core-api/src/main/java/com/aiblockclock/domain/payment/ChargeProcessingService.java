package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ChargeProcessingService {

    private final ChargeRequestReader chargeRequestReader;
    private final ChargeVerifier chargeVerifier;
    private final AbcConversionPolicy abcConversionPolicy;
    private final ChargeRequestWriter chargeRequestWriter;
    private final ChargeConfirmationManager chargeConfirmationManager;

    public ChargeRequestEntity process(
            Long chargeRequestId,
            OnChainUsdcTransfer onChainUsdcTransfer,
            ChargeNetworkPolicy chargeNetworkPolicy
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        if (chargeRequest.getStatus()
                != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        if (!chargeNetworkPolicy.supportsChain(
                chargeRequest.getChainId()
        )) {
            return chargeRequestWriter.reject(
                    chargeRequestId,
                    ChargeStatusReason.CHAIN_ID_MISMATCH
            );
        }

        ChargeVerificationCommand verificationCommand =
                chargeNetworkPolicy.createVerificationCommand(
                        chargeRequest
                );

        ChargeVerificationResult verificationResult =
                chargeVerifier.verify(
                        verificationCommand,
                        onChainUsdcTransfer
                );

        return switch (verificationResult.outcome()) {
            case WAITING -> chargeRequest;
            case REJECTED -> chargeRequestWriter.reject(
                    chargeRequestId,
                    verificationResult.reason()
            );
            case VERIFIED -> confirmCharge(chargeRequest);
        };
    }

    private ChargeRequestEntity confirmCharge(
            ChargeRequestEntity chargeRequest
    ) {
        BigDecimal creditedAbcAmount =
                abcConversionPolicy.convert(
                        chargeRequest.getRequestedUsdcAmount()
                );

        return chargeConfirmationManager.confirm(
                chargeRequest.getId(),
                creditedAbcAmount
        );
    }
}
