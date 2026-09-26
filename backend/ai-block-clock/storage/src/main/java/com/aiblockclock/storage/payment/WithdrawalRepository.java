package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.WithdrawalStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository extends JpaRepository<WithdrawalEntity, Long> {

    Optional<WithdrawalEntity> findByRequesterIdAndRequestId(Long requesterId, UUID requestId);

    boolean existsByRequesterIdAndStatusIn(Long requesterId, Collection<WithdrawalStatus> statuses);

    @Query("""
            SELECT MAX(withdrawal.createdAt)
              FROM WithdrawalEntity withdrawal
             WHERE withdrawal.requesterId = :requesterId
               AND withdrawal.status IN :statuses
            """)
    Optional<Instant> findLatestCreatedAt(
            @Param("requesterId") Long requesterId,
            @Param("statuses") Collection<WithdrawalStatus> statuses
    );

    // 읽은 뒤 다른 처리(취소 · 심사)가 먼저 상태를 바꿨으면 0 을 돌려준다. 늦은 쪽이 앞의 결과를 덮지 못한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WithdrawalEntity withdrawal
               SET withdrawal.status = :newStatus,
                   withdrawal.updatedAt = :updatedAt
             WHERE withdrawal.id = :withdrawalId
               AND withdrawal.status = :expectedStatus
            """)
    int updateStatusIfCurrent(
            @Param("withdrawalId") Long withdrawalId,
            @Param("expectedStatus") WithdrawalStatus expectedStatus,
            @Param("newStatus") WithdrawalStatus newStatus,
            @Param("updatedAt") Instant updatedAt
    );

    // 심사 결과와 새 상태를 한 번에 적는다. 조건은 updateStatusIfCurrent 와 같아 취소와 심사가 겹쳐도 한쪽만 된다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WithdrawalEntity withdrawal
               SET withdrawal.status = :newStatus,
                   withdrawal.reviewerId = :reviewerId,
                   withdrawal.reviewedAt = :reviewedAt,
                   withdrawal.rejectReason = :rejectReason,
                   withdrawal.updatedAt = :reviewedAt
             WHERE withdrawal.id = :withdrawalId
               AND withdrawal.status = :expectedStatus
            """)
    int updateReviewIfCurrent(
            @Param("withdrawalId") Long withdrawalId,
            @Param("expectedStatus") WithdrawalStatus expectedStatus,
            @Param("newStatus") WithdrawalStatus newStatus,
            @Param("reviewerId") Long reviewerId,
            @Param("reviewedAt") Instant reviewedAt,
            @Param("rejectReason") String rejectReason
    );

    List<WithdrawalEntity> findAllByStatusOrderByCreatedAtAscIdAsc(WithdrawalStatus status);
}
