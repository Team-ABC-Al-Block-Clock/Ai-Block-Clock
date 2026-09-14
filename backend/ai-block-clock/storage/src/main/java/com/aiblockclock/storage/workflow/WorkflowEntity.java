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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public WorkflowEntity(UUID id, Long ownerId, String name, String description,
                          ValidationStatus validationStatus, Long revision) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.validationStatus = validationStatus;
        this.revision = revision;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void applyGraph(List<WorkflowNodeEntity> nodes, List<WorkflowEdgeEntity> edges,
                           ValidationStatus validationStatus, Long revision) {
        mergeNodes(nodes);
        mergeEdges(edges);

        this.validationStatus = validationStatus;
        this.revision = revision;
    }

    /**
     * 그대로 남는 키는 행을 두고 값만 고친다. 이번 요청에 없는 키만 지우고, 새 키만 넣는다.
     *
     * <p>전부 지우고 다시 넣으면 같은 키가 한 flush 안에서 delete 와 insert 로 겹친다.
     * Hibernate 가 insert 를 먼저 내보내므로 {@code uk_workflow_node_key} 에 걸린다.
     */
    private void mergeNodes(List<WorkflowNodeEntity> incoming) {
        Map<String, WorkflowNodeEntity> gone = new HashMap<>();
        this.nodes.forEach(node -> gone.put(node.getNodeKey(), node));

        List<WorkflowNodeEntity> added = new ArrayList<>();
        for (WorkflowNodeEntity node : incoming) {
            WorkflowNodeEntity kept = gone.remove(node.getNodeKey());
            if (kept == null) {
                added.add(node);
                continue;
            }

            kept.refer(node.getSourceType(), node.getReferenceId(), node.getVersionId());
        }

        this.nodes.removeAll(gone.values());
        this.nodes.addAll(added);
    }

    private void mergeEdges(List<WorkflowEdgeEntity> incoming) {
        Map<String, WorkflowEdgeEntity> gone = new HashMap<>();
        this.edges.forEach(edge -> gone.put(edge.getEdgeKey(), edge));

        List<WorkflowEdgeEntity> added = new ArrayList<>();
        for (WorkflowEdgeEntity edge : incoming) {
            WorkflowEdgeEntity kept = gone.remove(edge.getEdgeKey());
            if (kept == null) {
                added.add(edge);
                continue;
            }

            kept.connect(edge.getSourceNodeKey(), edge.getTargetNodeKey());
        }

        this.edges.removeAll(gone.values());
        this.edges.addAll(added);
    }
}
