package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.request.PayoutConversionRequest;
import com.aiblockclock.api.v1.response.PayoutConversionResponse;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.PayoutConversionRequestId;
import com.aiblockclock.domain.payment.PayoutConversionService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits/payout-conversions")
@RequiredArgsConstructor
public class PayoutConversionController implements PayoutConversionApi {
    private final PayoutConversionService payoutConversionService;

    @Override
    @PostMapping
    public ApiResult<PayoutConversionResponse> convert(
            @CurrentUser UserId owner,
            @Valid @RequestBody PayoutConversionRequest request
    ) {
        return ApiResult.success(PayoutConversionResponse.from(payoutConversionService.convert(
                owner, PayoutConversionRequestId.of(request.requestId()), AbcAmount.of(request.amount()))));
    }
}
