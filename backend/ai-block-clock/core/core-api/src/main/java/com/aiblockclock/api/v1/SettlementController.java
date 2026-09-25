package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.v1.response.SettlementPageResponse;
import com.aiblockclock.api.v1.response.SettlementStatisticsResponse;
import com.aiblockclock.domain.execution.SettlementQueryService;
import com.aiblockclock.domain.execution.SettlementSearchCondition;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController implements SettlementApi {
    private final SettlementQueryService settlementQueryService;

    @Override
    @GetMapping
    public ApiResult<SettlementPageResponse> getSettlements(
            @CurrentUser UserId userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        SettlementSearchCondition condition = SettlementSearchCondition.of(from, to, agentId);

        return ApiResult.success(SettlementPageResponse.from(
                settlementQueryService.readHistory(userId, condition, cursor, size)));
    }

    @Override
    @GetMapping("/statistics")
    public ApiResult<SettlementStatisticsResponse> getStatistics(
            @CurrentUser UserId userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID agentId,
            @RequestParam(defaultValue = "DAY") SettlementPeriodUnit unit
    ) {
        SettlementSearchCondition condition = SettlementSearchCondition.of(from, to, agentId);

        return ApiResult.success(SettlementStatisticsResponse.from(unit,
                settlementQueryService.readStatistics(userId, condition, unit)));
    }
}
