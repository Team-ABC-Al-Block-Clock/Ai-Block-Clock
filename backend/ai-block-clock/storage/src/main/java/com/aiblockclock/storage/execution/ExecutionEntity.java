package com.aiblockclock.storage.execution;

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

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "run_id", length = 64, nullable = false)
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

    public ExecutionEntity(UUID id, UUID workflowId, Long requesterId, String runId,
                           ExecutionStatus status, int promptCount,
                           String inputFileKey, String inputFileName, Long inputFileSize) {
        this.id = id;
        this.workflowId = workflowId;
        this.requesterId = requesterId;
        this.runId = runId;
        this.status = status;
        this.promptCount = promptCount;
        this.inputFileKey = inputFileKey;
        this.inputFileName = inputFileName;
        this.inputFileSize = inputFileSize;
    }

    public void moveTo(ExecutionStatus status) {
        this.status = status;
    }
}
