package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
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
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "charge_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChargeRequestEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100, updatable = false)
    private String userReferenceId;

    @Column(nullable = false, length = 42, updatable = false)
    private String senderWalletAddress;

    @Column(nullable = false, updatable = false)
    private Long chainId;

    @Column(nullable = false, length = 66, updatable = false)
    private String transactionHash;

    @Column(
            nullable = false,
            precision = 38,
            scale = 6,
            updatable = false
    )
    private BigDecimal requestedUsdcAmount;

    @Column(precision = 38, scale = 3)
    private BigDecimal creditedAbcAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChargeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private ChargeStatusReason statusReason;

    @Version
    @Column(nullable = false)
    private Long version;

    private LocalDateTime confirmedAt;

    public static ChargeRequestEntity pending(
            String userReferenceId,
            String senderWalletAddress,
            Long chainId,
            String transactionHash,
            BigDecimal requestedUsdcAmount
    ) {
        ChargeRequestEntity entity = new ChargeRequestEntity();
        entity.userReferenceId = userReferenceId;
        entity.senderWalletAddress = senderWalletAddress;
        entity.chainId = chainId;
        entity.transactionHash = transactionHash;
        entity.requestedUsdcAmount = requestedUsdcAmount;
        entity.status = ChargeStatus.PENDING;
        return entity;
    }
}
