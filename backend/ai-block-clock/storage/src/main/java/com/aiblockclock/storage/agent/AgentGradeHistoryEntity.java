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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_grade_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentGradeHistoryEntity extends BaseCreatedAtEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "successful_runs", nullable = false, updatable = false)
    private long successfulRuns;

    @Column(name = "supplier_failures", nullable = false, updatable = false)
    private long supplierFailures;

    @Column(name = "review_count", nullable = false, updatable = false)
    private int reviewCount;

    @Column(name = "average_rating", precision = 3, scale = 2, updatable = false)
    private BigDecimal averageRating;

    @Column(name = "measured_at", updatable = false)
    private Instant measuredAt;

    @Column(name = "rating_score", precision = 9, scale = 6, nullable = false, updatable = false)
    private BigDecimal ratingScore;

    @Column(name = "usage_score", precision = 9, scale = 6, nullable = false, updatable = false)
    private BigDecimal usageScore;

    @Column(name = "success_rate", precision = 7, scale = 6, nullable = false, updatable = false)
    private BigDecimal successRate;

    @Column(name = "score", precision = 7, scale = 4, nullable = false, updatable = false)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "score_tier", length = 20, nullable = false, updatable = false)
    private Tier scoreTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", length = 20, nullable = false, updatable = false)
    private Tier tier;

    public AgentGradeHistoryEntity(
            UUID agentId, long successfulRuns, long supplierFailures, int reviewCount, BigDecimal averageRating,
            Instant measuredAt, BigDecimal ratingScore, BigDecimal usageScore, BigDecimal successRate,
            BigDecimal score, Tier scoreTier, Tier tier) {
        this.agentId = agentId;
        this.successfulRuns = successfulRuns;
        this.supplierFailures = supplierFailures;
        this.reviewCount = reviewCount;
        this.averageRating = averageRating;
        this.measuredAt = measuredAt;
        this.ratingScore = ratingScore;
        this.usageScore = usageScore;
        this.successRate = successRate;
        this.score = score;
        this.scoreTier = scoreTier;
        this.tier = tier;
    }
}
