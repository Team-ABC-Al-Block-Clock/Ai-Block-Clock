package com.aiblockclock.storage.workflow;

import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.audit.BaseTimeEntity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowEntity extends BaseTimeEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 20, nullable = false)
    private ValidationStatus validationStatus;

    @Column(name = "revision", nullable = false)
    private Long revision;

    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "workflow_id", nullable = false)
    private List<WorkflowNodeEntity> nodes = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "workflow_id", nullable = false)
    private List<WorkflowEdgeEntity> edges = new ArrayList<>();

    public WorkflowEntity(UUID id, Long ownerId, String name,
                          ValidationStatus validationStatus, Long revision) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.validationStatus = validationStatus;
        this.revision = revision;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void clearGraph() {
        this.nodes.clear();
        this.edges.clear();
    }

    public void applyGraph(List<WorkflowNodeEntity> nodes, List<WorkflowEdgeEntity> edges,
                           ValidationStatus validationStatus, Long revision) {
        this.nodes.addAll(nodes);
        this.edges.addAll(edges);
        this.validationStatus = validationStatus;
        this.revision = revision;
    }
}
