package com.aiblockclock.storage.execution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

// 실행 한 단계의 근거 한 줄이다. 실행을 닫는 트랜잭션에서 한 번 적고 고치지 않는다.
// 값 대부분은 엔진이 준 것이라 계약에 길이 · 범위가 없다. 칸을 넘으면 잘라 넣거나 비운다 —
// 한 줄이 넘쳐 저장이 실패하면 그 실행의 근거 전체가 적히지 않고, 백필이 와도 같은 자리에서 다시 실패한다.
@Entity
@Table(name = "execution_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionStepEntity {
    private static final BigDecimal MAX_COST = new BigDecimal("999999999999");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "step_id", length = 64, nullable = false, updatable = false)
    private String stepId;

    @Column(name = "node_name", length = 100, updatable = false)
    private String nodeName;

    @Column(name = "step_type", length = 20, updatable = false)
    private String stepType;

    @Column(name = "status", length = 20, updatable = false)
    private String status;

    @Column(name = "skip_reason", length = 64, updatable = false)
    private String skipReason;

    @Column(name = "branch_taken", updatable = false)
    private Boolean branchTaken;

    @Column(name = "owner_id", length = 64, updatable = false)
    private String ownerId;

    @Column(name = "agent_id", length = 64, updatable = false)
    private String agentId;

    @Column(name = "model", length = 64, updatable = false)
    private String model;

    @Column(name = "input_tokens", updatable = false)
    private Integer inputTokens;

    @Column(name = "output_tokens", updatable = false)
    private Integer outputTokens;

    @Column(name = "retries", updatable = false)
    private Integer retries;

    @Column(name = "repair_count", updatable = false)
    private Integer repairCount;

    @Column(name = "cost_usd", precision = 20, scale = 8, updatable = false)
    private BigDecimal costUsd;

    @Column(name = "started_at", length = 40, updatable = false)
    private String startedAt;

    @Column(name = "finished_at", length = 40, updatable = false)
    private String finishedAt;

    @Column(name = "duration_ms", updatable = false)
    private Long durationMs;

    @Column(name = "unit_price", precision = 41, scale = 6, updatable = false)
    private BigDecimal unitPrice;

    @Column(name = "settled_abc", precision = 41, scale = 6, updatable = false)
    private BigDecimal settledAbc;

    @Builder
    public ExecutionStepEntity(UUID executionId, String stepId, String nodeName, String stepType, String status,
                               String skipReason, Boolean branchTaken, String ownerId, String agentId, String model,
                               Integer inputTokens, Integer outputTokens, Integer retries, Integer repairCount,
                               BigDecimal costUsd, String startedAt, String finishedAt, Long durationMs,
                               BigDecimal unitPrice, BigDecimal settledAbc) {
        this.executionId = executionId;
        this.stepId = fit(stepId, 64);
        this.nodeName = fit(nodeName, 100);
        this.stepType = fit(stepType, 20);
        this.status = fit(status, 20);
        this.skipReason = fit(skipReason, 64);
        this.branchTaken = branchTaken;
        this.ownerId = fit(ownerId, 64);
        this.agentId = fit(agentId, 64);
        this.model = fit(model, 64);
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.retries = retries;
        this.repairCount = repairCount;
        this.costUsd = costOf(costUsd);
        this.startedAt = fit(startedAt, 40);
        this.finishedAt = fit(finishedAt, 40);
        this.durationMs = durationMs;
        this.unitPrice = unitPrice;
        this.settledAbc = settledAbc;
    }

    private static String fit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    private static BigDecimal costOf(BigDecimal value) {
        if (value == null || value.abs().compareTo(MAX_COST) > 0) {
            return null;
        }

        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
