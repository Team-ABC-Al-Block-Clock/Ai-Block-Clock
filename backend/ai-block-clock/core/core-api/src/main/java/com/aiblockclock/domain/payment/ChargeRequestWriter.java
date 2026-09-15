package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.storage.payment.ChargeRequestEntity;
import com.aiblockclock.storage.payment.ChargeRequestRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ChargeRequestWriter {

    private static final String CHAIN_HASH_UNIQUE_CONSTRAINT = "uq_charge_request_chain_hash";

    private final ChargeRequestRepository chargeRequestRepository;
    private final ChargeRequestReader chargeRequestReader;
    private final PlatformTransactionManager transactionManager;

    public ChargeRequest appendOrGetExisting(
            ChargeRequest chargeRequest
    ) {
        try {
            return newTransaction(false).execute(status -> insert(chargeRequest));
        } catch (DataIntegrityViolationException exception) {
            if (!isChainHashDuplicate(exception)) {
                throw exception;
            }

            return newTransaction(true).execute(status -> chargeRequestReader
                    .findByChainAndTransactionHash(
                            chargeRequest.getChainId(),
                            chargeRequest.getTransactionHash()
                    )
                    .orElseThrow(() -> exception));
        }
    }

    private ChargeRequest insert(ChargeRequest chargeRequest) {
        ChargeRequestEntity entity =
                ChargeRequestEntityConverter.toNewEntity(
                        chargeRequest
                );

        ChargeRequestEntity savedEntity =
                chargeRequestRepository.saveAndFlush(entity);

        return ChargeRequestEntityConverter.toDomain(savedEntity);
    }

    private TransactionTemplate newTransaction(boolean readOnly) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setReadOnly(readOnly);
        return transaction;
    }

    private boolean isChainHashDuplicate(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                return constraintName != null && constraintName.toLowerCase(Locale.ROOT)
                        .contains(CHAIN_HASH_UNIQUE_CONSTRAINT);
            }
        }
        return false;
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
