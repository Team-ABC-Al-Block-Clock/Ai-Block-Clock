package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.ChargeCreateRequest;
import com.aiblockclock.api.v1.response.ChargeStatusResponse;
import com.aiblockclock.domain.payment.ChargeProcessingService;
import com.aiblockclock.domain.payment.ChargeRequest;
import com.aiblockclock.domain.payment.ChargeRequestService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits/charges")
@RequiredArgsConstructor
public class ChargeController implements ChargeApi {
    private final ChargeRequestService chargeRequestService;
    private final ChargeProcessingService chargeProcessingService;

    // 요청을 저장한 뒤 곧바로 한 번 확인한다. 이미 블록에 적힌 거래면 사용자가 다시 묻지 않아도 끝난다
    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResult<ChargeStatusResponse> create(
            @CurrentUser UserId requester,
            @Valid @RequestBody ChargeCreateRequest request
    ) {
        ChargeRequest created = chargeRequestService.createChargeRequest(
                requester, request.transactionHash(), request.amount());
        ChargeRequest checked = chargeProcessingService.process(created.getId()).chargeRequest();

        return ApiResult.success(ChargeStatusResponse.from(checked));
    }

    @Override
    @GetMapping("/{chargeId}")
    public ApiResult<ChargeStatusResponse> get(
            @CurrentUser UserId requester,
            @PathVariable Long chargeId
    ) {
        return ApiResult.success(ChargeStatusResponse.from(
                chargeRequestService.getOwnedCharge(chargeId, requester)));
    }
}
