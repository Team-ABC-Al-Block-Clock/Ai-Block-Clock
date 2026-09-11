package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargeProcessingService {

    private final ChargeRequestReader chargeRequestReader;
    private final ChargeVerifier chargeVerifier;
    private final ChargeSettlementManager settlementManager;

    public ChargeRequestEntity process(
            Long chargeRequestId,
            OnChainUsdcTransfer transfer,
            ChargeNetworkPolicy networkPolicy
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        if (chargeRequest.getStatus()
                != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        if (!networkPolicy.supportsChain(
                chargeRequest.getChainId()
        )) {
            return settlementManager.applyResult(
                    chargeRequestId,
                    ChargeVerificationResult.rejected(
                            ChargeStatusReason.CHAIN_ID_MISMATCH
                    )
            );
        }

        ChargeVerificationCommand command =
                networkPolicy.createVerificationCommand(
                        chargeRequest
                );

        ChargeVerificationResult verificationResult =
                chargeVerifier.verify(
                        command,
                        transfer
                );

        return settlementManager.applyResult(
                chargeRequestId,
                verificationResult
        );
    }
}
