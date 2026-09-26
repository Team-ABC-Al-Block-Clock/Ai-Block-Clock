package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChargeRequestReader {

    private final ChargeRequestRepository chargeRequestRepository;

    public ChargeRequest read(Long chargeRequestId) {
        ChargeRequestEntity entity =
                chargeRequestRepository.findById(chargeRequestId)
                        .orElseThrow(
                                () -> new CoreException(
                                        ErrorType.CHARGE_REQUEST_NOT_FOUND
                                )
                        );

        return ChargeRequestEntityConverter.toDomain(entity);
    }

    public List<Long> readPendingIds() {
        return chargeRequestRepository.findIdsByStatus(ChargeStatus.PENDING);
    }

    public Optional<ChargeRequest> findByChainAndTransactionHash(
            Long chainId,
            String transactionHash
    ) {
        return chargeRequestRepository
                .findByChainIdAndTransactionHash(
                        chainId,
                        transactionHash
                )
                .map(ChargeRequestEntityConverter::toDomain);
    }
}
