package com.aiblockclock.domain.payment;

import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    public ChargeRequest update(
            ChargeRequest chargeRequest
    ) {
        ChargeRequestEntity entity =
                chargeRequestRepository
                        .findById(chargeRequest.getId())
                        .orElseThrow(
                                () -> new CoreException(
                                        ErrorType.CHARGE_REQUEST_NOT_FOUND
                                )
                        );

        ChargeRequestEntityConverter.applyToEntity(
                chargeRequest,
                entity
        );

        return ChargeRequestEntityConverter.toDomain(entity);
    }
}
