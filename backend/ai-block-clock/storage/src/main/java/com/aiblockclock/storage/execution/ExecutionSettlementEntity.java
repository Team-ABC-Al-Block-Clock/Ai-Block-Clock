package com.aiblockclock.storage.execution;

import com.aiblockclock.storage.audit.BaseCreatedAtEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "execution_settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionSettlementEntity extends BaseCreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "step_id", length = 16, nullable = false, updatable = false)
    private String stepId;

    @Column(name = "node_name", length = 40, updatable = false)
    private String nodeName;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, updatable = false)
    private UUID agentId;

    @Column(name = "amount_abc", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal amountAbc;

    public ExecutionSettlementEntity(
            UUID executionId, String stepId, String nodeName, Long supplierId, UUID agentId, BigDecimal amountAbc) {
        this.executionId = executionId;
        this.stepId = stepId;
        this.nodeName = nodeName;
        this.supplierId = supplierId;
        this.agentId = agentId;
        this.amountAbc = amountAbc;
    }
}
