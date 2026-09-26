package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.identity.IdentityVerification;
import com.aiblockclock.enums.identity.IdentityVerificationStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.time.LocalDate;

// 사용자가 보는 내 본인 확인과 관리자가 보는 심사 대상이 같은 모양이다
@Schema(description = "본인 확인 한 건")
public record IdentityVerificationResponse(
        @Schema(description = "본인 확인 번호. 관리자가 승인 · 반려할 때 쓴다", example = "7")
        String verificationId,

        @Schema(description = "제출한 사용자 ID", example = "12")
        String userId,

        @Schema(description = "SUBMITTED(심사 대기) · APPROVED(승인) · REJECTED(반려)", example = "SUBMITTED")
        IdentityVerificationStatus status,

        @Schema(description = "실명", example = "김현수")
        String legalName,

        @Schema(description = "생년월일", example = "1999-03-15")
        LocalDate birthDate,

        @Schema(description = "반려 사유. 반려됐을 때만 있다", nullable = true)
        String rejectReason,

        @Schema(description = "마지막으로 제출한 시각")
        Instant submittedAt,

        @Schema(description = "심사한 시각. 심사 전이면 없다", nullable = true)
        Instant reviewedAt
) {
    public static IdentityVerificationResponse from(IdentityVerification verification) {
        return new IdentityVerificationResponse(
                Long.toString(verification.getId()),
                Long.toString(verification.getUserId().value()),
                verification.getStatus(),
                verification.getLegalName(),
                verification.getBirthDate(),
                verification.getRejectReason(),
                verification.getSubmittedAt(),
                verification.getReviewedAt()
        );
    }
}
