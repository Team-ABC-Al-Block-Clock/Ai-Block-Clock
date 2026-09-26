package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityVerificationTest {
    private static final UserId OWNER = UserId.of(7L);
    private static final UserId ADMIN = UserId.of(1L);
    private static final LocalDate BIRTH_DATE = LocalDate.of(1999, 3, 15);

    @Test
    void 처음_제출하면_심사_대기다() {
        IdentityVerification verification = IdentityVerification.submitted(OWNER, "김현수", BIRTH_DATE);

        assertThat(verification.getStatus()).isEqualTo(IdentityVerificationStatus.SUBMITTED);
        assertThat(verification.isApproved()).isFalse();
    }

    @Test
    void 승인하면_승인한_관리자를_적는다() {
        IdentityVerification verification = saved(IdentityVerificationStatus.SUBMITTED);

        verification.approve(ADMIN);

        assertThat(verification.isApproved()).isTrue();
        assertThat(verification.getReviewer()).isEqualTo(ADMIN);
    }

    @Test
    void 반려하면_사유를_적는다() {
        IdentityVerification verification = saved(IdentityVerificationStatus.SUBMITTED);

        verification.reject(ADMIN, "실명이 다릅니다");

        assertThat(verification.isRejected()).isTrue();
        assertThat(verification.getRejectReason()).isEqualTo("실명이 다릅니다");
    }

    @ParameterizedTest
    @EnumSource(value = IdentityVerificationStatus.class, names = "SUBMITTED", mode = EnumSource.Mode.EXCLUDE)
    void 심사_대기가_아니면_승인도_반려도_할_수_없다(IdentityVerificationStatus status) {
        IdentityVerification verification = saved(status);

        assertThatThrownBy(() -> verification.approve(ADMIN))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.IDENTITY_VERIFICATION_INVALID_STATE));
        assertThatThrownBy(() -> verification.reject(ADMIN, "사유"))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.IDENTITY_VERIFICATION_INVALID_STATE));
    }

    @Test
    void 반려된_것은_고쳐_다시_내면_심사_대기로_돌아가고_지난_심사를_지운다() {
        IdentityVerification verification = saved(IdentityVerificationStatus.REJECTED);

        verification.resubmit("김현수", LocalDate.of(1999, 3, 16));

        assertThat(verification.getStatus()).isEqualTo(IdentityVerificationStatus.SUBMITTED);
        assertThat(verification.getBirthDate()).isEqualTo(LocalDate.of(1999, 3, 16));
        assertThat(verification.getReviewer()).isNull();
        assertThat(verification.getRejectReason()).isNull();
    }

    @Test
    void 심사_중에는_다시_낼_수_없다() {
        IdentityVerification verification = saved(IdentityVerificationStatus.SUBMITTED);

        assertThatThrownBy(() -> verification.resubmit("김현수", BIRTH_DATE))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.IDENTITY_VERIFICATION_ALREADY_SUBMITTED));
    }

    @Test
    void 승인된_뒤에는_다시_낼_수_없다() {
        IdentityVerification verification = saved(IdentityVerificationStatus.APPROVED);

        assertThatThrownBy(() -> verification.resubmit("김현수", BIRTH_DATE))
                .isInstanceOfSatisfying(CoreException.class, exception -> assertThat(exception.getErrorType())
                        .isEqualTo(ErrorType.IDENTITY_VERIFICATION_ALREADY_APPROVED));
    }

    private IdentityVerification saved(IdentityVerificationStatus status) {
        boolean reviewed = status != IdentityVerificationStatus.SUBMITTED;

        return IdentityVerification.restore(3L, OWNER, "김현수", BIRTH_DATE, status,
                Instant.parse("2026-09-26T09:00:00Z"),
                reviewed ? ADMIN : null,
                reviewed ? Instant.parse("2026-09-26T10:00:00Z") : null,
                status == IdentityVerificationStatus.REJECTED ? "실명이 다릅니다" : null);
    }
}
