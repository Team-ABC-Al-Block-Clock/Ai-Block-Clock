package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;
import com.aiblockclock.support.paging.CursorPage;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

// 공급자가 자기가 받은 정산을 본다. 늘 로그인 사용자 것만 읽고, 다른 공급자를 고를 입력을 받지 않는다.
// 정산금 잔액은 장부에서 읽으므로 ABC 잔액(AbcLedgerQueryService.readBalance)과 함께 나간다.
@Service
@RequiredArgsConstructor
public class SettlementQueryService {
    private final ExecutionSettlementReader executionSettlementReader;

    public CursorPage<SettlementRecord> readHistory(
            UserId supplier, SettlementSearchCondition condition, String cursor, int size) {
        return executionSettlementReader.readPage(supplier, condition, cursor, size);
    }

    public SettlementStatistics readStatistics(
            UserId supplier, SettlementSearchCondition condition, SettlementPeriodUnit unit) {
        return SettlementStatistics.of(executionSettlementReader.readAll(supplier, condition), unit);
    }
}
