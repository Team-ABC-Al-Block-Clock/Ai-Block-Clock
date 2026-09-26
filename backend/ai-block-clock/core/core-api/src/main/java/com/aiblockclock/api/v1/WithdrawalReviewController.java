package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.WithdrawalRejectRequest;
import com.aiblockclock.api.v1.response.PendingWithdrawalListResponse;
import com.aiblockclock.api.v1.response.WithdrawalResponse;
import com.aiblockclock.domain.payment.WithdrawalService;
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
@RequestMapping("/api/v1/admin/withdrawals")
@RequiredArgsConstructor
public class WithdrawalReviewController implements WithdrawalReviewApi {
    private final WithdrawalService withdrawalService;

    @Override
    @GetMapping
    public ApiResult<PendingWithdrawalListResponse> getPending() {
        return ApiResult.success(PendingWithdrawalListResponse.from(withdrawalService.readAllPending()));
    }

    @Override
    @PostMapping("/{withdrawalId}/approve")
    public ApiResult<WithdrawalResponse> approve(
            @CurrentUser UserId approver,
            @PathVariable Long withdrawalId
    ) {
        return ApiResult.success(WithdrawalResponse.from(withdrawalService.approve(approver, withdrawalId)));
    }

    @Override
    @PostMapping("/{withdrawalId}/reject")
    public ApiResult<WithdrawalResponse> reject(
            @CurrentUser UserId rejecter,
            @PathVariable Long withdrawalId,
            @Valid @RequestBody WithdrawalRejectRequest request
    ) {
        return ApiResult.success(WithdrawalResponse.from(
                withdrawalService.reject(rejecter, withdrawalId, request.reason())));
    }
}
