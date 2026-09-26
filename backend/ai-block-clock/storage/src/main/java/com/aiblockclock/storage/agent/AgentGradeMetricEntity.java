package com.aiblockclock.storage.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

// 읽기만 한다. 쓰기는 두 실행이 같은 줄을 동시에 더해도 하나를 잃지 않도록 리포지토리의 한 문장(addRuns)으로 한다.
@Entity
@Table(name = "agent_grade_metric")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentGradeMetricEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "successful_runs", nullable = false)
    private long successfulRuns;

    @Column(name = "supplier_failures", nullable = false)
    private long supplierFailures;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "measured_at", nullable = false)
    private Instant measuredAt;
}
