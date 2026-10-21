package com.aiblockclock.api.batch;

import com.aiblockclock.domain.execution.ExecutionEvidenceRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 정산 근거는 실행이 닫힌 뒤 따로 적는다. 큐가 넘쳤거나, 적다 실패했거나, 그 사이 서버가 내려가 빠진 근거를 주기마다 채운다.
// 실행의 핵심 흐름과 상관없이 돈다 — 여기가 늦거나 실패해도 실행 · 환불 · 정산은 이미 끝나 있다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "execution.evidence", name = "backfill-enabled", havingValue = "true",
        matchIfMissing = true)
public class ExecutionEvidenceBackfillScheduler {
    private static final int BATCH_SIZE = 50;

    private final ExecutionEvidenceRecorder executionEvidenceRecorder;

    @Scheduled(fixedDelayString = "${execution.evidence.backfill-interval:5m}",
            initialDelayString = "${execution.evidence.backfill-interval:5m}")
    public void backfill() {
        int recorded = executionEvidenceRecorder.backfill(BATCH_SIZE);

        if (recorded > 0) {
            log.info("빠진 정산 근거를 채웠다: {}건", recorded);
        }
    }
}
