package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ChargeRequestWriter {

    private final ChargeRequestRepository chargeRequestRepository;

    @Transactional
    public ChargeRequest append(
            ChargeRequest chargeRequest
    ) {
        ChargeRequestEntity entity =
                ChargeRequestEntityConverter.toNewEntity(
                        chargeRequest
                );

        ChargeRequestEntity savedEntity =
                chargeRequestRepository.save(entity);

        return ChargeRequestEntityConverter.toDomain(savedEntity);
    }

    @Transactional
    public ChargeRequest updateFromPending(
            ChargeRequest chargeRequest
    ) {
        int updatedRowCount = chargeRequestRepository
                .updateStateIfPendingAndVersionMatches(
                        chargeRequest.getId(),
                        chargeRequest.getVersion(),
                        ChargeStatus.PENDING,
                        chargeRequest.getStatus(),
                        chargeRequest.getStatusReason(),
                        chargeRequest.getCreditedAbcAmount(),
                        chargeRequest.getConfirmedAt(),
                        Instant.now()
                );

        if (updatedRowCount != 1) {
            throw new CoreException(
                    ErrorType.CHARGE_REQUEST_CONCURRENTLY_MODIFIED
            );
        }

        ChargeRequestEntity updatedEntity = chargeRequestRepository
                .findById(chargeRequest.getId())
                .orElseThrow(
                        () -> new CoreException(
                                ErrorType.CHARGE_REQUEST_NOT_FOUND
                        )
                );

        return ChargeRequestEntityConverter.toDomain(updatedEntity);
    }
}
