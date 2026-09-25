package com.aiblockclock.storage.payment;

import com.aiblockclock.enums.payment.ChargeStatus;
import com.aiblockclock.enums.payment.ChargeStatusReason;
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
