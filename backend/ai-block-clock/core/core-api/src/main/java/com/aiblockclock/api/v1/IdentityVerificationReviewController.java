package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.IdentityVerificationRejectRequest;
import com.aiblockclock.api.v1.response.IdentityVerificationListResponse;
import com.aiblockclock.api.v1.response.IdentityVerificationResponse;
import com.aiblockclock.domain.identity.IdentityVerificationService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 관리자 권한은 SecurityConfig 가 /api/v1/admin/** 에서 먼저 본다
@RestController
@RequestMapping("/api/v1/admin/identity-verifications")
@RequiredArgsConstructor
public class IdentityVerificationReviewController implements IdentityVerificationReviewApi {
    private final IdentityVerificationService identityVerificationService;

    @Override
    @GetMapping
    public ApiResult<IdentityVerificationListResponse> getSubmitted() {
        return ApiResult.success(IdentityVerificationListResponse.from(identityVerificationService.readAllSubmitted()));
    }

    @Override
    @PostMapping("/{verificationId}/approve")
    public ApiResult<IdentityVerificationResponse> approve(
            @CurrentUser UserId approver,
            @PathVariable Long verificationId
    ) {
        return ApiResult.success(IdentityVerificationResponse.from(
                identityVerificationService.approve(approver, verificationId)));
    }

    @Override
    @PostMapping("/{verificationId}/reject")
    public ApiResult<IdentityVerificationResponse> reject(
            @CurrentUser UserId rejecter,
            @PathVariable Long verificationId,
            @Valid @RequestBody IdentityVerificationRejectRequest request
    ) {
        return ApiResult.success(IdentityVerificationResponse.from(
                identityVerificationService.reject(rejecter, verificationId, request.reason())));
    }
}
