package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "agent_version_node")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentVersionNodeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_version_id", length = 36, nullable = false, updatable = false)
    private UUID agentVersionId;

    @Column(name = "node_key", length = 64, nullable = false, updatable = false)
    private String nodeKey;

    @Column(name = "name", length = 40, nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 20, nullable = false, updatable = false)
    private NodeType nodeType;

    @Column(name = "prompt", nullable = false, updatable = false)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "model", length = 40, nullable = false, updatable = false)
    private AiModel model;

    @Column(name = "base_cost", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal baseCost;

    public AgentVersionNodeEntity(UUID agentVersionId, String nodeKey, String name, NodeType nodeType,
                                  String prompt, AiModel model, BigDecimal baseCost) {
        this.agentVersionId = agentVersionId;
        this.nodeKey = nodeKey;
        this.name = name;
        this.nodeType = nodeType;
        this.prompt = prompt;
        this.model = model;
        this.baseCost = baseCost;
    }
}
