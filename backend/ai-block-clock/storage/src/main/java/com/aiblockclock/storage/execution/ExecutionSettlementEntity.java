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
    private static final int STEP_ID_LENGTH = 16;
    private static final int NODE_NAME_LENGTH = 40;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "step_id", length = STEP_ID_LENGTH, nullable = false, updatable = false)
    private String stepId;

    @Column(name = "node_name", length = NODE_NAME_LENGTH, updatable = false)
    private String nodeName;

    @Column(name = "supplier_id", nullable = false, updatable = false)
    private Long supplierId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, updatable = false)
    private UUID agentId;

    @Column(name = "amount_abc", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal amountAbc;

    // 단계 번호와 노드 이름은 엔진이 돌려준 값이고 계약에 길이가 없다. 칸보다 길면 잘라 넣는다 (ExecutionFailureEntity 와 같다).
    // 넘친 채 저장하다 실패하면 같은 트랜잭션의 실행 닫기 · 환불까지 되돌려져, 모니터가 올 때마다 같은 자리에서 다시 실패한다.
    public ExecutionSettlementEntity(
            UUID executionId, String stepId, String nodeName, Long supplierId, UUID agentId, BigDecimal amountAbc) {
        this.executionId = executionId;
        this.stepId = fit(stepId, STEP_ID_LENGTH);
        this.nodeName = fit(nodeName, NODE_NAME_LENGTH);
        this.supplierId = supplierId;
        this.agentId = agentId;
        this.amountAbc = amountAbc;
    }

    private static String fit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
