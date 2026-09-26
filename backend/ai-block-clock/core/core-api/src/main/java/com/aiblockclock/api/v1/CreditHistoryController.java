package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.CreditHistoryPageResponse;
import com.aiblockclock.domain.payment.CreditHistoryService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.CreditHistoryType;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits/history")
@RequiredArgsConstructor
public class CreditHistoryController implements CreditHistoryApi {
    private final CreditHistoryService creditHistoryService;

    @Override
    @GetMapping
    public ApiResult<CreditHistoryPageResponse> getHistory(
            @CurrentUser UserId owner,
            @RequestParam(required = false) CreditHistoryType type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResult.success(CreditHistoryPageResponse.from(
                creditHistoryService.read(owner, type, cursor, size)));
    }
}
