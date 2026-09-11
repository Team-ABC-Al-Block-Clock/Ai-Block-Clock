package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChargeSettlementManager {

    private final ChargeRequestRepository chargeRequestRepository;
    private final AbcConversionPolicy conversionPolicy;
    private final AbcChargeLedgerWriter ledgerWriter;

    @Transactional
    public ChargeRequestEntity applyResult(
            Long chargeRequestId,
            ChargeVerificationResult verificationResult
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestRepository.findById(chargeRequestId)
                        .orElseThrow(
                                () -> new CoreException(
                                        ErrorType.CHARGE_REQUEST_NOT_FOUND
                                )
                        );

        if (chargeRequest.getStatus()
                != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        return switch (verificationResult.outcome()) {
            case WAITING -> chargeRequest;
            case REJECTED -> rejectCharge(
                    chargeRequest,
                    verificationResult.reason()
            );
            case VERIFIED -> confirmCharge(chargeRequest);
        };
    }

    private ChargeRequestEntity rejectCharge(
            ChargeRequestEntity chargeRequest,
            ChargeStatusReason reason
    ) {
        chargeRequest.fail(reason);
        return chargeRequest;
    }

    private ChargeRequestEntity confirmCharge(
            ChargeRequestEntity chargeRequest
    ) {
        BigDecimal abcAmount =
                conversionPolicy.convert(
                        chargeRequest.getRequestedUsdcAmount()
                );

        ledgerWriter.recordCharge(
                chargeRequest,
                abcAmount
        );

        chargeRequest.confirm(
                abcAmount,
                Instant.now()
        );

        return chargeRequest;
    }
}
