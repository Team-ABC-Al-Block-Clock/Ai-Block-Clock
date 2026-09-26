package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.storage.identity.IdentityVerificationRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class IdentityVerificationWriter {

    // V1__create_account.sql 의 사용자당 한 줄 제약 이름이다.
    private static final String USER_UNIQUE_CONSTRAINT = "uq_identity_verification_user";

    private final IdentityVerificationRepository identityVerificationRepository;
    private final Clock clock;

    // 처음 제출이 두 번 겹치면(연타) 늦은 쪽이 사용자당 한 줄 제약에 막힌다. 먼저 들어간 제출이 심사를 기다린다.
    @Transactional
    public IdentityVerification append(IdentityVerification verification) {
        try {
            return IdentityVerificationEntityConverter.toDomain(identityVerificationRepository.saveAndFlush(
                    IdentityVerificationEntityConverter.toNewEntity(verification, now())));
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateUser(exception)) {
                throw exception;
            }

            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_ALREADY_SUBMITTED);
        }
    }

    // 반려된 제출을 도메인에서 고친 정보로 덮는다. 그사이 다른 다시 내기가 먼저 덮었으면 심사 중이라고 답한다.
    @Transactional
    public IdentityVerification modifySubmission(IdentityVerification verification) {
        int updatedRowCount = identityVerificationRepository.updateSubmissionIfCurrent(
                verification.getId(),
                IdentityVerificationStatus.REJECTED,
                verification.getStatus(),
                verification.getLegalName(),
                verification.getBirthDate(),
                now());

        if (updatedRowCount != 1) {
            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_ALREADY_SUBMITTED);
        }

        return reread(verification.getId());
    }

    // 도메인에서 내린 심사 결과를 적는다. 심사 대기가 아니게 됐으면(다른 관리자가 먼저 심사) 덮지 않고 실패한다.
    @Transactional
    public IdentityVerification modifyReview(IdentityVerification verification) {
        UserId reviewer = verification.getReviewer();
        int updatedRowCount = identityVerificationRepository.updateReviewIfCurrent(
                verification.getId(),
                IdentityVerificationStatus.SUBMITTED,
                verification.getStatus(),
                reviewer.value(),
                now(),
                verification.getRejectReason());

        if (updatedRowCount != 1) {
            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_INVALID_STATE);
        }

        return reread(verification.getId());
    }

    private IdentityVerification reread(Long verificationId) {
        return identityVerificationRepository.findById(verificationId)
                .map(IdentityVerificationEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.IDENTITY_VERIFICATION_NOT_FOUND));
    }

    // DATETIME(6) 에 담기는 정밀도로 맞춘다. 돌려준 값과 다시 읽은 값이 같아진다
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private boolean isDuplicateUser(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                // DB 가 제약 이름에 접두 · 접미 표현을 붙일 수 있어 대소문자를 맞춘 뒤 포함 여부로 본다.
                return constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(USER_UNIQUE_CONSTRAINT);
            }
        }

        return false;
    }
}
