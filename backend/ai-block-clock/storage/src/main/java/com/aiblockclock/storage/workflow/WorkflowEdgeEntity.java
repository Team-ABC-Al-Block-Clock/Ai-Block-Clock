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

@Entity
@Table(name = "workflow_edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowEdgeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "edge_key", length = 64, nullable = false)
    private String edgeKey;

    @Column(name = "source_node_key", length = 64, nullable = false)
    private String sourceNodeKey;

    @Column(name = "target_node_key", length = 64, nullable = false)
    private String targetNodeKey;

    public WorkflowEdgeEntity(String edgeKey, String sourceNodeKey, String targetNodeKey) {
        this.edgeKey = edgeKey;
        this.sourceNodeKey = sourceNodeKey;
        this.targetNodeKey = targetNodeKey;
    }
}
