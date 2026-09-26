package com.aiblockclock.domain.payment;

import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.storage.payment.WithdrawalRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class WithdrawalWriter {

    // V33__create_withdrawal.sql 의 요청 번호 중복 제약 이름이다.
    private static final String REQUEST_UNIQUE_CONSTRAINT = "uq_withdrawal_requester_request";

    private final WithdrawalRepository withdrawalRepository;

    // 같은 요청 번호가 두 번 들어오면 늦은 쪽이 여기서 막힌다.
    // 제약 위반을 이 자리에서 알아채려고 SQL 을 곧바로 내보낸다. 트랜잭션이 실패하면 함께 되돌려진다.
    @Transactional
    public Withdrawal append(Withdrawal withdrawal) {
        try {
            return WithdrawalEntityConverter.toDomain(
                    withdrawalRepository.saveAndFlush(WithdrawalEntityConverter.toNewEntity(withdrawal)));
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateRequest(exception)) {
                throw exception;
            }

            throw new CoreException(ErrorType.WITHDRAWAL_ALREADY_REQUESTED);
        }
    }

    // 도메인에서 바꾼 상태를 적는다. 읽은 뒤 다른 처리가 먼저 상태를 바꿨으면 덮지 않고 실패한다.
    @Transactional
    public Withdrawal changeStatus(Withdrawal withdrawal, WithdrawalStatus expectedStatus) {
        int updatedRowCount = withdrawalRepository.updateStatusIfCurrent(
                withdrawal.getId(), expectedStatus, withdrawal.getStatus(), Instant.now());

        if (updatedRowCount != 1) {
            throw new CoreException(ErrorType.WITHDRAWAL_INVALID_STATE);
        }

        return withdrawalRepository.findById(withdrawal.getId())
                .map(WithdrawalEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.WITHDRAWAL_NOT_FOUND));
    }

    // 도메인에서 내린 심사 결과를 적는다. 심사 대기가 아니게 됐으면(사용자 취소 · 다른 관리자의 심사) 덮지 않고 실패한다.
    @Transactional
    public Withdrawal modifyReview(Withdrawal withdrawal) {
        int updatedRowCount = withdrawalRepository.updateReviewIfCurrent(
                withdrawal.getId(),
                WithdrawalStatus.REQUESTED,
                withdrawal.getStatus(),
                withdrawal.getReviewer().value(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                withdrawal.getRejectReason());

        if (updatedRowCount != 1) {
            throw new CoreException(ErrorType.WITHDRAWAL_INVALID_STATE);
        }

        return withdrawalRepository.findById(withdrawal.getId())
                .map(WithdrawalEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.WITHDRAWAL_NOT_FOUND));
    }

    private boolean isDuplicateRequest(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                // DB 가 제약 이름에 접두 · 접미 표현을 붙일 수 있어 대소문자를 맞춘 뒤 포함 여부로 본다.
                return constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(REQUEST_UNIQUE_CONSTRAINT);
            }
        }

        return false;
    }
}
