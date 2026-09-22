package com.aiblockclock.storage.workflow;

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

import java.util.UUID;

@Entity
@Table(name = "workflow_edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowEdgeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "workflow_id", length = 36, nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "edge_key", length = 64, nullable = false)
    private String edgeKey;

    @Column(name = "source_node_key", length = 64, nullable = false)
    private String sourceNodeKey;

    @Column(name = "target_node_key", length = 64, nullable = false)
    private String targetNodeKey;

    public WorkflowEdgeEntity(UUID workflowId, String edgeKey, String sourceNodeKey, String targetNodeKey) {
        this.workflowId = workflowId;
        this.edgeKey = edgeKey;
        this.sourceNodeKey = sourceNodeKey;
        this.targetNodeKey = targetNodeKey;
    }
}
