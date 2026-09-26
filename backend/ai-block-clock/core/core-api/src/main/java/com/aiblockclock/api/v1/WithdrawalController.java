package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.WithdrawalCreateRequest;
import com.aiblockclock.api.v1.response.WithdrawalResponse;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.WithdrawalRequestId;
import com.aiblockclock.domain.payment.WithdrawalService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController implements WithdrawalApi {
    private final WithdrawalService withdrawalService;

    // 심사를 거쳐 지급되므로 받았다는 뜻의 202 로 답한다
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<WithdrawalResponse> create(
            @CurrentUser UserId requester,
            @Valid @RequestBody WithdrawalCreateRequest request
    ) {
        return ApiResult.success(WithdrawalResponse.from(withdrawalService.request(
                requester, WithdrawalRequestId.of(request.requestId()), AbcAmount.of(request.amount()))));
    }

    @Override
    @PostMapping("/{withdrawalId}/cancel")
    public ApiResult<WithdrawalResponse> cancel(
            @CurrentUser UserId requester,
            @PathVariable Long withdrawalId
    ) {
        return ApiResult.success(WithdrawalResponse.from(withdrawalService.cancel(requester, withdrawalId)));
    }
}
