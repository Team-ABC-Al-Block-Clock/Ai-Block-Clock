package com.aiblockclock.storage.identity;

import com.aiblockclock.enums.identity.IdentityVerificationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IdentityVerificationRepository extends JpaRepository<IdentityVerificationEntity, Long> {

    Optional<IdentityVerificationEntity> findByUserId(Long userId);

    boolean existsByUserIdAndStatus(Long userId, IdentityVerificationStatus status);

    List<IdentityVerificationEntity> findAllByUserIdIn(Collection<Long> userIds);

    List<IdentityVerificationEntity> findAllByStatusOrderBySubmittedAtAscIdAsc(IdentityVerificationStatus status);

    // 반려된 제출만 새 정보로 덮고 심사 대기로 되돌린다. 지난 심사 기록은 비운다.
    // 그사이 다른 제출이 먼저 덮었으면 0 을 돌려준다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IdentityVerificationEntity verification
               SET verification.legalName = :legalName,
                   verification.birthDate = :birthDate,
                   verification.status = :newStatus,
                   verification.submittedAt = :submittedAt,
                   verification.reviewerId = null,
                   verification.reviewedAt = null,
                   verification.rejectReason = null,
                   verification.updatedAt = :submittedAt
             WHERE verification.id = :verificationId
               AND verification.status = :expectedStatus
            """)
    int updateSubmissionIfCurrent(
            @Param("verificationId") Long verificationId,
            @Param("expectedStatus") IdentityVerificationStatus expectedStatus,
            @Param("newStatus") IdentityVerificationStatus newStatus,
            @Param("legalName") String legalName,
            @Param("birthDate") LocalDate birthDate,
            @Param("submittedAt") Instant submittedAt
    );

    // 심사 대기인 제출에만 심사 결과를 적는다. 두 관리자가 겹치면 늦은 쪽은 0 을 받는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE IdentityVerificationEntity verification
               SET verification.status = :newStatus,
                   verification.reviewerId = :reviewerId,
                   verification.reviewedAt = :reviewedAt,
                   verification.rejectReason = :rejectReason,
                   verification.updatedAt = :reviewedAt
             WHERE verification.id = :verificationId
               AND verification.status = :expectedStatus
            """)
    int updateReviewIfCurrent(
            @Param("verificationId") Long verificationId,
            @Param("expectedStatus") IdentityVerificationStatus expectedStatus,
            @Param("newStatus") IdentityVerificationStatus newStatus,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("rejectReason") String rejectReason
    );
}
