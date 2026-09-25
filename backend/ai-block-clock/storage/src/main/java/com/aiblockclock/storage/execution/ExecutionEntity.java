package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.audit.BaseTimeEntity;

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
import java.util.UUID;

@Entity
@Table(name = "execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionEntity extends BaseTimeEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "workflow_id", length = 36, nullable = false)
    private UUID workflowId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_id", length = 36, nullable = false)
    private UUID requestId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ExecutionStatus status;

    @Column(name = "prompt_count", nullable = false)
    private int promptCount;

    @Column(name = "input_file_key", length = 128)
    private String inputFileKey;

    @Column(name = "input_file_name", length = 255)
    private String inputFileName;

    @Column(name = "input_file_size")
    private Long inputFileSize;

    @Column(name = "reserved_abc", precision = 41, scale = 6)
    private BigDecimal reservedAbc;

    @Column(name = "failed_step_id", length = 16)
    private String failedStepId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_fault", length = 20)
    private ExecutionFault failureFault;

    @Column(name = "failure_affects_success_rate")
    private Boolean failureAffectsSuccessRate;

    @Column(name = "fault_supplier_id")
    private Long faultSupplierId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "fault_agent_id", length = 36)
    private UUID faultAgentId;

    public ExecutionEntity(UUID id, UUID workflowId, UUID requestId, Long requesterId, String runId,
                           ExecutionStatus status, int promptCount,
                           String inputFileKey, String inputFileName, Long inputFileSize,
                           BigDecimal reservedAbc) {
        this.id = id;
        this.workflowId = workflowId;
        this.requestId = requestId;
        this.requesterId = requesterId;
        this.runId = runId;
        this.status = status;
        this.promptCount = promptCount;
        this.inputFileKey = inputFileKey;
        this.inputFileName = inputFileName;
        this.inputFileSize = inputFileSize;
        this.reservedAbc = reservedAbc;
    }

    public void moveTo(ExecutionStatus status) {
        this.status = status;
    }

    public void recordFailure(String failedStepId, String failureCode, ExecutionFault failureFault,
                              boolean failureAffectsSuccessRate, Long faultSupplierId, UUID faultAgentId) {
        this.failedStepId = failedStepId;
        this.failureCode = failureCode;
        this.failureFault = failureFault;
        this.failureAffectsSuccessRate = failureAffectsSuccessRate;
        this.faultSupplierId = faultSupplierId;
        this.faultAgentId = faultAgentId;
    }

    public void markDispatched(String runId) {
        this.runId = runId;
    }
}
