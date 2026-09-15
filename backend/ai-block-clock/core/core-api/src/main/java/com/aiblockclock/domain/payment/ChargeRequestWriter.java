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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ChargeRequestWriter {

    // V2__create_charge_request_table.sql의 거래 중복 제약 이름과 연결되며, 변경 시 ChargeRequestWriterTest도 확인한다.
    private static final String CHAIN_HASH_UNIQUE_CONSTRAINT = "uq_charge_request_chain_hash";

    private final ChargeRequestRepository chargeRequestRepository;

    // 중복 저장 실패의 롤백이 끝난 뒤 Service가 재조회하도록 저장을 독립된 트랜잭션으로 실행한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ChargeRequest saveNewRequest(
            ChargeRequest chargeRequest
    ) {
        try {
            ChargeRequestEntity entity =
                    ChargeRequestEntityConverter.toNewEntity(chargeRequest);

            // 중복 제약 오류를 여기서 감지해 도메인 오류로 바꾸도록 SQL을 즉시 실행한다.
            // SQL 실행은 최종 저장 확정이 아니며, 트랜잭션이 실패하면 롤백된다.
            ChargeRequestEntity savedEntity =
                    chargeRequestRepository.saveAndFlush(entity);

            return ChargeRequestEntityConverter.toDomain(savedEntity);
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateTransaction(exception)) {
                throw exception;
            }

            CoreException duplicateRequestException = new CoreException(
                    ErrorType.CHARGE_REQUEST_ALREADY_EXISTS
            );
            duplicateRequestException.initCause(exception);
            throw duplicateRequestException;
        }
    }

    private boolean isDuplicateTransaction(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();
                // DB가 제약 이름에 접두·접미 표현을 붙일 수 있어 대소문자를 통일한 뒤 포함 여부로 비교한다.
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
