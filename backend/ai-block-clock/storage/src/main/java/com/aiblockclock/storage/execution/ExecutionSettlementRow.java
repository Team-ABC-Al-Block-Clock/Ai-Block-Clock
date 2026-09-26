package com.aiblockclock.storage.execution;

// 정산 한 줄과 그 실행을 요청한 사람이다. 받은 사람과 요청한 사람이 같으면 자기 실행이다.
// 요청한 사람은 execution 에만 있어 한 쿼리에서 함께 읽어 온다.
public record ExecutionSettlementRow(ExecutionSettlementEntity settlement, Long requesterId) {
}
