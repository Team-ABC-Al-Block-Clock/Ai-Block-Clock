package com.aiblockclock.domain.payment;

import com.aiblockclock.domain.payment.ChargeNetworkPolicy;
import com.aiblockclock.domain.payment.ChargeVerificationCommand;
import com.aiblockclock.domain.payment.ChargeVerificationResult;
import com.aiblockclock.domain.payment.ChargeVerifier;
import com.aiblockclock.domain.payment.OnChainUsdcTransfer;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class ChargeProcessingService {

    private final ChargeRequestRepository chargeRequestRepository;
    private final ChargeVerifier chargeVerifier;
    private final ChargeSettlementService settlementService;

    public ChargeProcessingService(
            ChargeRequestRepository chargeRequestRepository,
            ChargeVerifier chargeVerifier,
            ChargeSettlementService settlementService
    ) {
        this.chargeRequestRepository = chargeRequestRepository;
        this.chargeVerifier = chargeVerifier;
        this.settlementService = settlementService;
    }

    @Transactional
    public ChargeRequestEntity process(
            Long chargeRequestId,
            OnChainUsdcTransfer transfer,
            ChargeNetworkPolicy networkPolicy
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.findById(chargeRequestId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "충전 요청을 찾을 수 없습니다."
                                )
                        );

        if (chargeRequest.getStatus()
                != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        if (!Objects.equals(
                chargeRequest.getChainId(),
                networkPolicy.chainId()
        )) {
            return settlementService.applyResult(
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

        return settlementService.applyResult(
                chargeRequestId,
                verificationResult
        );
    }
}
