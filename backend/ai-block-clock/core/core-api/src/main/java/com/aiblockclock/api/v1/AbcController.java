package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.AbcBalanceResponse;
import com.aiblockclock.api.v1.response.AbcTransactionPageResponse;
import com.aiblockclock.domain.payment.AbcLedgerQueryService;
import com.aiblockclock.domain.payment.AbcTransaction;
import com.aiblockclock.domain.payment.AbcTransactionSearchCondition;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class AbcController implements AbcApi {
    private final AbcLedgerQueryService abcLedgerQueryService;

    @Override
    @GetMapping("/balance")
    public ApiResult<AbcBalanceResponse> getBalance(@CurrentUser UserId userId) {
        return ApiResult.success(AbcBalanceResponse.from(abcLedgerQueryService.readBalance(userId)));
    }

    @Override
    @GetMapping("/transactions")
    public ApiResult<AbcTransactionPageResponse> getTransactions(
            @CurrentUser UserId userId,
            @RequestParam(required = false) LedgerEventType transactionType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        AbcTransactionSearchCondition condition = AbcTransactionSearchCondition.of(from, to, transactionType);
        CursorPage<AbcTransaction> page = abcLedgerQueryService.readTransactions(userId, condition, cursor, size);

        return ApiResult.success(AbcTransactionPageResponse.from(page));
    }
}
