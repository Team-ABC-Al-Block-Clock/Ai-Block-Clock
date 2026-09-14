package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.audit.BaseTimeEntity;
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

import java.math.BigDecimal;

@Getter
@Entity
@Table(name = "ledger_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccountEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private LedgerAccountType accountType;

    @Column(nullable = false, length = 100, updatable = false)
    private String referenceId;

    @Column(nullable = false, precision = 38, scale = 3)
    private BigDecimal balance = BigDecimal.ZERO.setScale(3);

    @Version
    @Column(nullable = false)
    private Long version;

    public static LedgerAccountEntity of(
            LedgerAccountType accountType,
            String referenceId
    ) {
        LedgerAccountEntity entity = new LedgerAccountEntity();
        entity.accountType = accountType;
        entity.referenceId = referenceId;
        return entity;
    }

    public void applyBalanceDelta(BigDecimal amountDelta) {
        balance = balance.add(amountDelta);
    }
}
