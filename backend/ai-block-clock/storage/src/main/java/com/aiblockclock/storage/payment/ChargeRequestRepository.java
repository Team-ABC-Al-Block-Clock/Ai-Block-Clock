package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ChargeRequestRepository
        extends JpaRepository<ChargeRequestEntity, Long> {

    // 먼저 들어온 요청부터 확인한다. 대기 한도가 먼저 차는 쪽이다
    @Query("""
            SELECT chargeRequest.id
              FROM ChargeRequestEntity chargeRequest
             WHERE chargeRequest.status = :status
             ORDER BY chargeRequest.id
            """)
    List<Long> findIdsByStatus(@Param("status") ChargeStatus status);

    Optional<ChargeRequestEntity> findByChainIdAndTransactionHash(
            Long chainId,
            String transactionHash
    );

    // 한 사람의 충전 요청을 최신순으로 앞에서부터 limit 개 읽는다. 출금과 합친 내역을 만들 때 쓴다
    List<ChargeRequestEntity> findAllByUserReferenceIdOrderByCreatedAtDescIdDesc(
            String userReferenceId,
            Limit limit
    );

    // 닫힌 줄을 새 요청으로 바꿔 끼우기 전에 지운다. 읽은 뒤 누가 먼저 바꿨으면 버전이 달라 0 을 돌려준다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM ChargeRequestEntity chargeRequest
             WHERE chargeRequest.id = :chargeRequestId
               AND chargeRequest.status = :failedStatus
               AND chargeRequest.version = :expectedVersion
            """)
    int deleteIfFailedAndVersionMatches(
            @Param("chargeRequestId") Long chargeRequestId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("failedStatus") ChargeStatus failedStatus
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ChargeRequestEntity chargeRequest
               SET chargeRequest.creditedAbcAmount = :creditedAbcAmount,
                   chargeRequest.status = :newStatus,
                   chargeRequest.statusReason = :statusReason,
                   chargeRequest.confirmedAt = :confirmedAt,
                   chargeRequest.updatedAt = :updatedAt,
                   chargeRequest.version = chargeRequest.version + 1
             WHERE chargeRequest.id = :chargeRequestId
               AND chargeRequest.status = :pendingStatus
               AND chargeRequest.version = :expectedVersion
            """)
    int updateStateIfPendingAndVersionMatches(
            @Param("chargeRequestId") Long chargeRequestId,
            @Param("expectedVersion") Long expectedVersion,
            @Param("pendingStatus") ChargeStatus pendingStatus,
            @Param("newStatus") ChargeStatus newStatus,
            @Param("statusReason") ChargeStatusReason statusReason,
            @Param("creditedAbcAmount") BigDecimal creditedAbcAmount,
            @Param("confirmedAt") Instant confirmedAt,
            @Param("updatedAt") Instant updatedAt
    );
}
