package com.aiblockclock.storage.identity;

import com.aiblockclock.enums.identity.IdentityVerificationStatus;
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

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Entity
@Table(name = "identity_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerificationEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "legal_name", length = 50, nullable = false)
    private String legalName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IdentityVerificationStatus status;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    // 아래 셋은 관리자가 심사한 뒤에만 있다. 조건부 UPDATE(IdentityVerificationRepository)로만 적는다
    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    public static IdentityVerificationEntity of(
            Long userId,
            String legalName,
            LocalDate birthDate,
            IdentityVerificationStatus status,
            Instant submittedAt
    ) {
        IdentityVerificationEntity entity = new IdentityVerificationEntity();
        entity.userId = userId;
        entity.legalName = legalName;
        entity.birthDate = birthDate;
        entity.status = status;
        entity.submittedAt = submittedAt;
        return entity;
    }
}
