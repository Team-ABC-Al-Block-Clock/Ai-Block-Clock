package com.aiblockclock.application.payment;

import com.aiblockclock.domain.payment.AbcConversionPolicy;
import com.aiblockclock.domain.payment.ChargeVerificationResult;
import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class ChargeSettlementService {

    private final ChargeRequestRepository chargeRequestRepository;
    private final AbcConversionPolicy conversionPolicy;
    private final AbcChargeLedgerWriter ledgerWriter;

    public ChargeSettlementService(
            ChargeRequestRepository chargeRequestRepository,
            AbcConversionPolicy conversionPolicy,
            AbcChargeLedgerWriter ledgerWriter
    ) {
        this.chargeRequestRepository = chargeRequestRepository;
        this.conversionPolicy = conversionPolicy;
        this.ledgerWriter = ledgerWriter;
    }

    @Transactional
    public ChargeRequestEntity applyResult(
            Long chargeRequestId,
            ChargeVerificationResult verificationResult
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

        return switch (verificationResult.outcome()) {
            case WAITING -> chargeRequest;

            case REJECTED -> {
                chargeRequest.fail(
                        verificationResult.reason()
                );
                yield chargeRequest;
            }

            case VERIFIED -> {
                BigDecimal abcAmount =
                        conversionPolicy.convert(
                                chargeRequest
                                        .getRequestedUsdcAmount()
                        );

                ledgerWriter.recordCharge(
                        chargeRequest,
                        abcAmount
                );

                chargeRequest.confirm(
                        abcAmount,
                        LocalDateTime.now()
                );

                yield chargeRequest;
            }
        };
    }
}
