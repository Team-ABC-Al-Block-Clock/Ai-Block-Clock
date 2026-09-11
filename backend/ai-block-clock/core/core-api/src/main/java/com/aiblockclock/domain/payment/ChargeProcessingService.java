package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

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

        if (!Objects.equals(
                chargeRequest.getChainId(),
                networkPolicy.chainId()
        )) {
            return settlementManager.applyResult(
                    chargeRequestId,
                    ChargeVerificationResult.rejected(
                            ChargeStatusReason.CHAIN_ID_MISMATCH
                    )
            );
        }

        ChargeVerificationCommand command =
                new ChargeVerificationCommand(
                        networkPolicy.chainId(),
                        chargeRequest.getTransactionHash(),
                        chargeRequest.getSenderWalletAddress(),
                        chargeRequest.getRequestedUsdcAmount(),
                        networkPolicy.custodyAddress(),
                        networkPolicy.usdcContractAddress(),
                        networkPolicy.requiredConfirmations()
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
