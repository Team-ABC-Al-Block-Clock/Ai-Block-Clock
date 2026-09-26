package com.aiblockclock.storage.node;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
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
@Table(name = "node_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NodeDefinitionEntity extends BaseTimeEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id", length = 36, nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "name", length = 40, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", length = 20, nullable = false)
    private NodeType nodeType;

    @Column(name = "prompt", columnDefinition = "text", nullable = false)
    private String prompt;

    @Enumerated(EnumType.STRING)
    @Column(name = "model", length = 40, nullable = false)
    private AiModel model;

    @Column(name = "base_cost", precision = 41, scale = 6, nullable = false)
    private BigDecimal baseCost;

    public NodeDefinitionEntity(UUID id, Long ownerId, String name, NodeType nodeType, String prompt,
                                AiModel model, BigDecimal baseCost) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.nodeType = nodeType;
        this.prompt = prompt;
        this.model = model;
        this.baseCost = baseCost;
    }

    public void edit(String name, String prompt, AiModel model, BigDecimal baseCost) {
        this.name = name;
        this.prompt = prompt;
        this.model = model;
        this.baseCost = baseCost;
    }

    public void reprice(BigDecimal baseCost) {
        this.baseCost = baseCost;
    }
}
