package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ledger_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30, updatable = false)
    private LedgerAccountType accountType;

    @Column(name = "reference_id", nullable = false, length = 100, updatable = false)
    private String referenceId;

    @Column(nullable = false, precision = 38, scale = 3)
    private BigDecimal balance = BigDecimal.ZERO.setScale(3);

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static LedgerAccountEntity of(
            LedgerAccountType accountType,
            String referenceId
    ) {
        LedgerAccountEntity entity = new LedgerAccountEntity();
        entity.accountType = accountType;
        entity.referenceId = referenceId;
        return entity;
    }

    public void modifyBalance(BigDecimal balance) {
        this.balance = balance;
    }
}