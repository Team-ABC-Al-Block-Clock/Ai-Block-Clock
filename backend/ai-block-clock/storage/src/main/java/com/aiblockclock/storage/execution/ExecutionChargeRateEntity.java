package com.aiblockclock.storage.execution;

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

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "execution_charge_rate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExecutionChargeRateEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "execution_id", length = 36, nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "step_id", length = 16, nullable = false, updatable = false)
    private String stepId;

    @Column(name = "price_multiplier", precision = 6, scale = 4, nullable = false, updatable = false)
    private BigDecimal priceMultiplier;

    public ExecutionChargeRateEntity(UUID executionId, String stepId, BigDecimal priceMultiplier) {
        this.executionId = executionId;
        this.stepId = stepId;
        this.priceMultiplier = priceMultiplier;
    }
}
