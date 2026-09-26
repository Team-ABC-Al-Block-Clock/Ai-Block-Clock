package com.aiblockclock.storage.node;

import com.aiblockclock.enums.node.NodeTestRunStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_test_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeTestRunEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "node_id", length = 36, nullable = false, updatable = false)
    private UUID nodeId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "mock", nullable = false, updatable = false)
    private boolean mock;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private NodeTestRunStatus status;

    @Column(name = "cost_usd", precision = 20, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public NodeTestRunEntity(UUID id, UUID nodeId, Long requesterId, boolean mock, Instant requestedAt) {
        this.id = id;
        this.nodeId = nodeId;
        this.requesterId = requesterId;
        this.mock = mock;
        this.status = NodeTestRunStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    public void dispatch(String runId) {
        this.runId = runId;
        this.status = NodeTestRunStatus.RUNNING;
    }

    public void finish(NodeTestRunStatus status, BigDecimal costUsd, String failureCode, Instant finishedAt) {
        this.status = status;
        this.costUsd = costUsd;
        this.failureCode = failureCode;
        this.finishedAt = finishedAt;
    }
}
