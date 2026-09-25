package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionFault;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

// 실행 ID 를 우리가 정해 넣으므로, 새 줄이라고 알려 주지 않으면 저장할 때 이미 있는 줄을 덮어쓴다.
// 늘 새로 넣게 해 같은 실행의 판정을 두 번 적으면 PK 가 막게 한다.
@Entity
@Table(name = "execution_failure")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionFailureEntity implements Persistable<UUID> {
    private static final int STEP_ID_LENGTH = 16;
    private static final int CODE_LENGTH = 64;

    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "failed_step_id", length = STEP_ID_LENGTH, updatable = false)
    private String failedStepId;

    @Column(name = "failure_code", length = CODE_LENGTH, updatable = false)
    private String failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "fault", length = 20, nullable = false, updatable = false)
    private ExecutionFault fault;

    @Column(name = "affects_success_rate", nullable = false, updatable = false)
    private boolean affectsSuccessRate;

    @Column(name = "supplier_id", updatable = false)
    private Long supplierId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, updatable = false)
    private UUID agentId;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean stored;

    // 단계 번호와 오류 코드는 엔진이 보낸 값이고 계약에 길이가 없다. 칸보다 길면 잘라 넣는다.
    // 넘친 채 저장하다 실패하면 같은 트랜잭션의 실행 닫기와 환불까지 되돌려져, 실행이 영영 닫히지 않는다.
    public ExecutionFailureEntity(UUID executionId, String failedStepId, String failureCode, ExecutionFault fault,
                                  boolean affectsSuccessRate, Long supplierId, UUID agentId) {
        this.executionId = executionId;
        this.failedStepId = fit(failedStepId, STEP_ID_LENGTH);
        this.failureCode = fit(failureCode, CODE_LENGTH);
        this.fault = fault;
        this.affectsSuccessRate = affectsSuccessRate;
        this.supplierId = supplierId;
        this.agentId = agentId;
    }

    @Override
    public UUID getId() {
        return executionId;
    }

    @Override
    public boolean isNew() {
        return !stored;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        stored = true;
    }

    private static String fit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
