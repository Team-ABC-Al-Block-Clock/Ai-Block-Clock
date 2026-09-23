package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.audit.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "agent_version")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentVersionEntity extends BaseTimeEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_id", nullable = false, updatable = false)
    private AgentEntity agent;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "graph_id", length = 36, nullable = false, updatable = false)
    private UUID graphId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_workflow_id", length = 36, nullable = false)
    private UUID sourceWorkflowId;

    @Column(name = "source_revision", nullable = false)
    private Long sourceRevision;

    @Column(name = "prompt_node_count", nullable = false)
    private Integer promptNodeCount;

    @Column(name = "base_cost", precision = 41, scale = 6, nullable = false)
    private BigDecimal baseCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false)
    private Tier tier;

    @Enumerated(EnumType.STRING)
    @Column(name = "publication_state", length = 20, nullable = false)
    private PublicationState publicationState;

    public AgentVersionEntity(UUID id, AgentEntity agent, Integer versionNo, UUID graphId, UUID sourceWorkflowId,
                              Long sourceRevision, Integer promptNodeCount, BigDecimal baseCost,
                              Tier tier, PublicationState publicationState) {
        this.id = id;
        this.agent = agent;
        this.graphId = graphId;
        this.versionNo = versionNo;
        this.sourceWorkflowId = sourceWorkflowId;
        this.sourceRevision = sourceRevision;
        this.promptNodeCount = promptNodeCount;
        this.baseCost = baseCost;
        this.tier = tier;
        this.publicationState = publicationState;
    }
}
