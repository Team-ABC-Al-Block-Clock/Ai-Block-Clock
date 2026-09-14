package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChargeRequestWriter {

    private final ChargeRequestReader chargeRequestReader;

    @Transactional
    public ChargeRequestEntity reject(
            Long chargeRequestId,
            ChargeStatusReason rejectionReason
    ) {
        ChargeRequestEntity chargeRequest =
                chargeRequestReader.read(chargeRequestId);

        if (chargeRequest.getStatus() != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        chargeRequest.fail(rejectionReason);
        return chargeRequest;
    }

    @Transactional
    public ChargeRequestEntity confirm(
            ChargeRequestEntity chargeRequest,
            BigDecimal creditedAbcAmount,
            Instant confirmedAt
    ) {
        if (chargeRequest.getStatus() != ChargeStatus.PENDING) {
            return chargeRequest;
        }

        chargeRequest.confirm(
                creditedAbcAmount,
                confirmedAt
        );
        return chargeRequest;
    }
}
