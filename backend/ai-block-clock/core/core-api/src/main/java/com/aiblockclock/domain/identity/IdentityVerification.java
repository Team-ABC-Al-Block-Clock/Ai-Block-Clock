package com.aiblockclock.domain.identity;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

// 출금 전에 받는 본인 확인이다(ACC-004). 지갑 서명은 "이 지갑의 주인" 까지만 말해 주므로, 그 뒤의 실제 사람을 적어 두고 관리자가 확인한다.
// 사용자마다 하나다. 반려되면 고쳐서 다시 내고, 승인된 뒤에는 바꾸지 않는다.
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class IdentityVerification {

    private final Long id;
    private final UserId userId;
    private String legalName;
    private LocalDate birthDate;
    private IdentityVerificationStatus status;
    // 아래는 저장된 뒤에만 있다. 시각은 Writer 가 적는다
    private final Instant submittedAt;
    private UserId reviewer;
    private final Instant reviewedAt;
    private String rejectReason;

    public static IdentityVerification submitted(UserId userId, String legalName, LocalDate birthDate) {
        return new IdentityVerification(
                null, userId, legalName, birthDate, IdentityVerificationStatus.SUBMITTED, null, null, null, null);
    }

    public static IdentityVerification restore(
            Long id,
            UserId userId,
            String legalName,
            LocalDate birthDate,
            IdentityVerificationStatus status,
            Instant submittedAt,
            UserId reviewer,
            Instant reviewedAt,
            String rejectReason
    ) {
        return new IdentityVerification(
                id, userId, legalName, birthDate, status, submittedAt, reviewer, reviewedAt, rejectReason);
    }

    public boolean isApproved() {
        return status == IdentityVerificationStatus.APPROVED;
    }

    public boolean isRejected() {
        return status == IdentityVerificationStatus.REJECTED;
    }

    // 반려된 것만 고쳐 다시 낸다. 심사 중에 또 내면 관리자가 보던 정보가 바뀌고, 승인된 뒤에 바꾸면 확인한 사람이 달라진다.
    public void resubmit(String newLegalName, LocalDate newBirthDate) {
        if (status == IdentityVerificationStatus.SUBMITTED) {
            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_ALREADY_SUBMITTED);
        }

        if (status == IdentityVerificationStatus.APPROVED) {
            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_ALREADY_APPROVED);
        }

        legalName = newLegalName;
        birthDate = newBirthDate;
        status = IdentityVerificationStatus.SUBMITTED;
        reviewer = null;
        rejectReason = null;
    }

    public void approve(UserId approver) {
        checkSubmitted();

        status = IdentityVerificationStatus.APPROVED;
        reviewer = approver;
    }

    public void reject(UserId rejecter, String reason) {
        checkSubmitted();

        status = IdentityVerificationStatus.REJECTED;
        reviewer = rejecter;
        rejectReason = reason;
    }

    private void checkSubmitted() {
        if (status != IdentityVerificationStatus.SUBMITTED) {
            throw new CoreException(ErrorType.IDENTITY_VERIFICATION_INVALID_STATE);
        }
    }
}
