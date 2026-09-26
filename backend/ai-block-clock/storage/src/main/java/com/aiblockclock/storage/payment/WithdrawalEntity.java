package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.WithdrawalStatus;
import com.aiblockclock.storage.audit.BaseTimeEntity;

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

@Getter
@Entity
@Table(name = "withdrawal")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WithdrawalEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "request_id", length = 36, nullable = false, updatable = false)
    private UUID requestId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "amount_abc", precision = 41, scale = 6, nullable = false, updatable = false)
    private BigDecimal amountAbc;

    @Column(name = "wallet_address", length = 42, nullable = false, updatable = false)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private WithdrawalStatus status;

    // 아래 셋은 관리자가 심사한 뒤에만 있다. 조건부 UPDATE(WithdrawalRepository.updateReviewIfCurrent)로만 적는다
    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    // 아래 넷은 지급을 시작한 뒤에만 있다. 조건부 UPDATE(WithdrawalRepository.updatePayoutStartIfApproved ·
    // updatePaidIfPaying)로만 적는다
    @Column(name = "payout_transaction_hash", length = 66)
    private String payoutTransactionHash;

    @Column(name = "payout_raw_transaction", columnDefinition = "text")
    private String payoutRawTransaction;

    @Column(name = "payout_submitted_at")
    private Instant payoutSubmittedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    public static WithdrawalEntity of(
            UUID requestId,
            Long requesterId,
            BigDecimal amountAbc,
            String walletAddress,
            WithdrawalStatus status
    ) {
        WithdrawalEntity entity = new WithdrawalEntity();
        entity.requestId = requestId;
        entity.requesterId = requesterId;
        entity.amountAbc = amountAbc;
        entity.walletAddress = walletAddress;
        entity.status = status;
        return entity;
    }
}
