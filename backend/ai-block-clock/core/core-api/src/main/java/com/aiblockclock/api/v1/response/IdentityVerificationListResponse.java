package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.identity.IdentityVerification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "심사를 기다리는 본인 확인. 오래 기다린 것부터다")
public record IdentityVerificationListResponse(
        @Schema(description = "심사 대기 본인 확인")
        List<IdentityVerificationResponse> items
) {
    public static IdentityVerificationListResponse from(List<IdentityVerification> verifications) {
        return new IdentityVerificationListResponse(verifications.stream()
                .map(IdentityVerificationResponse::from)
                .toList());
    }
}
