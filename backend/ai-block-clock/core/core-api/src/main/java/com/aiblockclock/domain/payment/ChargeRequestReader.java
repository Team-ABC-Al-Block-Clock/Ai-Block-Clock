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
public class ChargeRequestReader {

    private final ChargeRequestRepository chargeRequestRepository;

    @Transactional(readOnly = true)
    public ChargeRequestEntity read(Long chargeRequestId) {
        return chargeRequestRepository.findById(chargeRequestId)
                .orElseThrow(
                        () -> new CoreException(
                                ErrorType.CHARGE_REQUEST_NOT_FOUND
                        )
                );
    }
}
