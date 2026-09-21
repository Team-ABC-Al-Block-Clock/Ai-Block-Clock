package com.aiblockclock.storage.workflow;

import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "workflow_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "node_key", length = 64, nullable = false)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 10, nullable = false)
    private SourceType sourceType;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "reference_id", length = 36, nullable = false)
    private UUID referenceId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "version_id", length = 36)
    private UUID versionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "input_source", length = 20, nullable = false)
    private InputSource inputSource;

    public WorkflowNodeEntity(UUID workflowId, String nodeKey, SourceType sourceType,
                              UUID referenceId, UUID versionId, InputSource inputSource) {
        this.workflowId = workflowId;
        this.nodeKey = nodeKey;
        this.sourceType = sourceType;
        this.referenceId = referenceId;
        this.versionId = versionId;
        this.inputSource = inputSource;
    }

    public void refer(SourceType sourceType, UUID referenceId, UUID versionId, InputSource inputSource) {
        this.sourceType = sourceType;
        this.referenceId = referenceId;
        this.versionId = versionId;
        this.inputSource = inputSource;
    }
}
