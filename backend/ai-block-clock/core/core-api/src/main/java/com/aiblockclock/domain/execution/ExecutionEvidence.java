package com.aiblockclock.domain.execution;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// 실행 하나를 두고 정산을 다툴 때 꺼내 보이는 것 전부다. 무엇을 떼고(선차감) 돌려주고(환불) 누구에게 줬는지(정산)와,
// 그렇게 판단한 근거(단계마다 엔진이 알린 사실 · 실패 판정 · 엔진 원본)를 한자리에 모은다. 모두 닫을 때 적고 고치지 않은 기록이다.
public record ExecutionEvidence(
        Summary execution,
        Failure failure,
        List<ChargeRate> chargeRates,
        List<Step> steps,
        List<Settlement> settlements,
        List<LedgerTransaction> ledger,
        RunRecord runRecord
) {
    public record Summary(UUID id, Long requesterId, UUID workflowId, String status, String runId, boolean mock,
                          BigDecimal reservedAbc, Instant requestedAt, Instant updatedAt) {
    }

    public record Failure(String failedStepId, String failureCode, String fault, boolean affectsSuccessRate,
                          Long supplierId, UUID agentId) {
    }

    public record ChargeRate(String stepId, BigDecimal priceMultiplier, BigDecimal unitPrice) {
    }

    public record Step(String stepId, String nodeName, String type, String status, String skipReason,
                       Boolean branchTaken, String ownerId, String agentId, String model, Integer inputTokens,
                       Integer outputTokens, Integer retries, Integer repairCount, BigDecimal costUsd,
                       String startedAt, String finishedAt, Long durationMs, BigDecimal unitPrice,
                       BigDecimal settledAbc) {
    }

    public record Settlement(String stepId, String nodeName, Long supplierId, UUID agentId, BigDecimal amountAbc,
                             Instant settledAt) {
    }

    public record LedgerTransaction(String eventType, String idempotencyKey, Instant createdAt,
                                    List<LedgerEntry> entries) {
    }

    public record LedgerEntry(String accountType, String accountReferenceId, BigDecimal amountDelta) {
    }

    // response 는 엔진이 준 원본(JSON 문자열)이다. sha256 으로 저장한 그것인지 맞춰 볼 수 있다.
    public record RunRecord(String closedBy, String runId, String engineStatus, String response, String sha256,
                            String note, Instant recordedAt) {
    }
}
