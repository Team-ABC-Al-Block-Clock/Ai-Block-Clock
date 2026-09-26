package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.WithdrawalStatus;

import org.springframework.data.domain.Limit;
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

    // 승인이 오래된 것부터 지급한다. 승인 시각이 같으면 먼저 신청한 것부터다
    Optional<WithdrawalEntity> findFirstByStatusOrderByReviewedAtAscIdAsc(WithdrawalStatus status);

    // 보낸 전송과 새 상태를 한 번에 적는다. 이미 다른 차례가 지급을 시작했으면 0 을 돌려준다 — 한 신청에 거래는 하나다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WithdrawalEntity withdrawal
               SET withdrawal.status = com.aiblockclock.enums.payment.WithdrawalStatus.PAYING,
                   withdrawal.payoutTransactionHash = :transactionHash,
                   withdrawal.payoutRawTransaction = :rawTransaction,
                   withdrawal.payoutSubmittedAt = :submittedAt,
                   withdrawal.updatedAt = :submittedAt
             WHERE withdrawal.id = :withdrawalId
               AND withdrawal.status = com.aiblockclock.enums.payment.WithdrawalStatus.APPROVED
            """)
    int updatePayoutStartIfApproved(
            @Param("withdrawalId") Long withdrawalId,
            @Param("transactionHash") String transactionHash,
            @Param("rawTransaction") String rawTransaction,
            @Param("submittedAt") Instant submittedAt
    );

    // 읽었을 때의 상태(지급 중 · 보류)가 그대로일 때만 끝낸다. 두 차례가 같은 성공을 봐도 한쪽만 끝내고, 늦은 쪽은 0 을 받는다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WithdrawalEntity withdrawal
               SET withdrawal.status = com.aiblockclock.enums.payment.WithdrawalStatus.PAID,
                   withdrawal.paidAt = :paidAt,
                   withdrawal.updatedAt = :paidAt
             WHERE withdrawal.id = :withdrawalId
               AND withdrawal.status = :expectedStatus
            """)
    int updatePaidIfCurrent(
            @Param("withdrawalId") Long withdrawalId,
            @Param("expectedStatus") WithdrawalStatus expectedStatus,
            @Param("paidAt") Instant paidAt
    );

    // 체인에 보낸 뒤 아직 끝나지 않은 신청(지급 중 · 보류)이다. 한 번에 한 건만 보내므로 많아야 하나다
    Optional<WithdrawalEntity> findFirstByStatusInOrderByReviewedAtAscIdAsc(Collection<WithdrawalStatus> statuses);

    // 한 사람의 신청을 최신순으로 앞에서부터 limit 개 읽는다. 충전과 합친 내역을 만들 때 쓴다
    List<WithdrawalEntity> findAllByRequesterIdOrderByCreatedAtDescIdDesc(Long requesterId, Limit limit);
}
