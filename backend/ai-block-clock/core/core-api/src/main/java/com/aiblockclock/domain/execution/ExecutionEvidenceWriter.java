package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionRunRecordEntity;
import com.aiblockclock.storage.execution.ExecutionRunRecordRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.execution.ExecutionStepEntity;
import com.aiblockclock.storage.execution.ExecutionStepRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 정산을 다툴 때 꺼내 보는 근거를 적는다. 실행을 닫는 트랜잭션과 따로, 닫기가 커밋된 뒤에 제 트랜잭션으로 적는다 —
// 근거를 적다 실패해도 실행 닫기 · 환불 · 정산은 이미 끝나 있다. 빠진 근거는 백필이 다시 적는다(ExecutionEvidenceRecorder).
// 단계 표에는 엔진이 알린 사실과 우리가 내린 판단(접수 때 굳힌 단가 · 실제 적립액)을 나란히 둔다. 판단은 DB 에 남은 것을 읽는다.
@Component
@RequiredArgsConstructor
public class ExecutionEvidenceWriter {
    // mediumtext 는 16MB 바이트다. 한글은 3바이트라 넉넉히 잡는다. 넘으면 원본은 빼고 지문만 남긴다.
    static final int MAX_RESPONSE_CHARS = 4_000_000;

    private final ExecutionRepository executionRepository;
    private final ExecutionReader executionReader;
    private final ExecutionSettlementRepository executionSettlementRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final ExecutionRunRecordRepository executionRunRecordRepository;
    private final Clock clock;

    // 이미 적었으면 아무것도 하지 않고 false 다. 커밋 직후 이벤트와 백필이 겹쳐도 한 번만 적힌다(겹쳐 적으면 PK 가 막는다).
    @Transactional
    public boolean record(ExecutionId id, EngineReport report) {
        if (executionRunRecordRepository.existsById(id.value())) {
            return false;
        }

        ExecutionEntity execution = executionRepository.findById(id.value()).orElse(null);

        if (execution == null) {
            return false;
        }

        executionStepRepository.saveAll(stepsOf(id, report.run(), executionReader.readStepPrices(id),
                executionSettlementRepository.findAllByExecutionIdOrderByIdAsc(id.value())));
        executionRunRecordRepository.save(recordOf(execution, report));

        return true;
    }

    private static List<ExecutionStepEntity> stepsOf(ExecutionId id, RunStatus run, StepPrices prices,
                                                     List<ExecutionSettlementEntity> settlements) {
        if (run == null || run.nodes() == null) {
            return List.of();
        }

        Map<String, BigDecimal> settled = settlements.stream()
                .collect(Collectors.toMap(ExecutionSettlementEntity::getStepId, ExecutionSettlementEntity::getAmountAbc,
                        BigDecimal::add));
        Set<String> seen = new HashSet<>();
        List<ExecutionStepEntity> steps = new ArrayList<>();

        // 같은 단계 번호가 두 번 오면(엔진 오류) 앞의 것만 둔다. 유니크 제약에 걸리면 근거 전체가 적히지 않는다.
        for (RunStatus.Node node : run.nodes()) {
            if (node.id() == null || !seen.add(node.id())) {
                continue;
            }

            steps.add(ExecutionStepEntity.builder()
                    .executionId(id.value())
                    .stepId(node.id())
                    .nodeName(node.name())
                    .stepType(node.type())
                    .status(node.status())
                    .skipReason(node.skipReason())
                    .branchTaken(node.branchTaken())
                    .ownerId(node.ownerId())
                    .agentId(node.agentId())
                    .model(node.model())
                    .inputTokens(node.inputTokens())
                    .outputTokens(node.outputTokens())
                    .retries(node.retries())
                    .repairCount(node.repairCount())
                    .costUsd(node.costUsd())
                    .startedAt(node.startedAt())
                    .finishedAt(node.finishedAt())
                    .durationMs(node.durationMs())
                    .unitPrice(prices.of(node.id()).map(price -> price.price().value()).orElse(null))
                    .settledAbc(settled.get(node.id()))
                    .build());
        }

        return steps;
    }

    private ExecutionRunRecordEntity recordOf(ExecutionEntity execution, EngineReport report) {
        String raw = report.raw();
        String response = raw;
        String note = null;

        if (raw == null && report.run() != null) {
            note = "엔진 원본을 받지 못했다. 단계 표는 마지막으로 본 엔진 상태로 채웠다";
        } else if (raw != null && raw.length() > MAX_RESPONSE_CHARS) {
            response = null;
            note = "원본이 커서(" + raw.length() + "자) 지문만 남겼다";
        }

        return new ExecutionRunRecordEntity(
                execution.getId(),
                execution.getRunId(),
                report.closedBy().name(),
                report.run() == null ? null : report.run().status(),
                response,
                raw == null ? null : sha256(raw),
                note,
                clock.instant());
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없다", exception);
        }
    }
}
