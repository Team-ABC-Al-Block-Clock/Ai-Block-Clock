package com.aiblockclock.domain.execution;

import java.time.Instant;

// 공급자가 받은 정산 한 줄이다. 어느 실행의 어느 단계로 얼마를 언제 받았는지 담는다.
// selfUse 는 자기 에이전트 · 노드를 자기가 실행해 받은 것이다. 정산금에는 들어오지만 등급 집계(303)에서는 뺀다.
public record SettlementRecord(
        ExecutionId executionId,
        ExecutionSettlement settlement,
        boolean selfUse,
        Instant settledAt
) {
}
