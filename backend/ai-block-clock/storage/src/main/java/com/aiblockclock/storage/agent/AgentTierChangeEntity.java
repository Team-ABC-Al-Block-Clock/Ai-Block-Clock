package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.audit.BaseCreatedAtEntity;

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
@Table(name = "agent_tier_change")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentTierChangeEntity extends BaseCreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, nullable = false, updatable = false)
    private UUID agentId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_version_id", length = 36, nullable = false, updatable = false)
    private UUID agentVersionId;

    @Column(name = "grade_history_id", nullable = false, updatable = false)
    private Long gradeHistoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "before_tier", length = 20, nullable = false, updatable = false)
    private Tier beforeTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "after_tier", length = 20, nullable = false, updatable = false)
    private Tier afterTier;

    @Column(name = "before_price", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal beforePrice;

    @Column(name = "after_price", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal afterPrice;

    public AgentTierChangeEntity(UUID agentId, UUID agentVersionId, Long gradeHistoryId, Tier beforeTier,
                                 Tier afterTier, BigDecimal beforePrice, BigDecimal afterPrice) {
        this.agentId = agentId;
        this.agentVersionId = agentVersionId;
        this.gradeHistoryId = gradeHistoryId;
        this.beforeTier = beforeTier;
        this.afterTier = afterTier;
        this.beforePrice = beforePrice;
        this.afterPrice = afterPrice;
    }
}
