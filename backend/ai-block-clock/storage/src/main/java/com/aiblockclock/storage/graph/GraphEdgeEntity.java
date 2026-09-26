package com.aiblockclock.storage.graph;

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
@Table(name = "graph_edge")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GraphEdgeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "graph_id", length = 36, nullable = false, updatable = false)
    private UUID graphId;

    @Column(name = "edge_key", length = 64, nullable = false)
    private String edgeKey;

    @Column(name = "source_node_key", length = 64, nullable = false)
    private String sourceNodeKey;

    @Column(name = "target_node_key", length = 64, nullable = false)
    private String targetNodeKey;

    @Column(name = "branch_when")
    private Boolean branchWhen;

    public GraphEdgeEntity(UUID graphId, String edgeKey, String sourceNodeKey, String targetNodeKey,
                           Boolean branchWhen) {
        this.graphId = graphId;
        this.edgeKey = edgeKey;
        this.sourceNodeKey = sourceNodeKey;
        this.targetNodeKey = targetNodeKey;
        this.branchWhen = branchWhen;
    }
}
