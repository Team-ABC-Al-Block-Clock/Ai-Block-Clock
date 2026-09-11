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
import java.time.Instant;

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

    private Instant confirmedAt;

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

    public void confirm(
            BigDecimal creditedAbcAmount,
            Instant confirmedAt
    ) {
        if (status != ChargeStatus.PENDING) {
            throw new IllegalStateException(
                    "대기 중인 충전 요청만 확정할 수 있습니다."
            );
        }

        if (creditedAbcAmount == null
                || creditedAbcAmount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "지급할 ABC 수량은 0보다 커야 합니다."
            );
        }

        if (confirmedAt == null) {
            throw new IllegalArgumentException(
                    "충전 확정 시각은 필수입니다."
            );
        }

        this.creditedAbcAmount = creditedAbcAmount;
        this.status = ChargeStatus.CONFIRMED;
        this.statusReason = null;
        this.confirmedAt = confirmedAt;
    }

    public void fail(ChargeStatusReason reason) {
        if (status != ChargeStatus.PENDING) {
            throw new IllegalStateException(
                    "대기 중인 충전 요청만 실패 처리할 수 있습니다."
            );
        }

        if (reason == null
                || reason == ChargeStatusReason.USER_CANCELED) {
            throw new IllegalArgumentException(
                    "올바른 충전 실패 사유가 필요합니다."
            );
        }

        this.status = ChargeStatus.FAILED;
        this.statusReason = reason;
        this.creditedAbcAmount = null;
        this.confirmedAt = null;
    }

    public void cancel() {
        if (status == ChargeStatus.CANCELED) {
            return;
        }

        if (status != ChargeStatus.PENDING) {
            throw new IllegalStateException(
                    "대기 중인 충전 요청만 취소할 수 있습니다."
            );
        }

        this.status = ChargeStatus.CANCELED;
        this.statusReason = ChargeStatusReason.USER_CANCELED;
        this.creditedAbcAmount = null;
        this.confirmedAt = null;
    }
}
