package com.aiblockclock.storage.execution;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionSettlementRepository extends JpaRepository<ExecutionSettlementEntity, Long> {

    // 받은 사람을 조건에서 빼지 않는다. 다른 공급자의 내역을 고를 방법을 쿼리에 두지 않으려는 것이다.
    // 커서 페이지는 전체 개수를 쓰지 않으므로 Page 대신 List 로 받아 count 쿼리를 내지 않는다.
    @Query("""
            SELECT new com.aiblockclock.storage.execution.ExecutionSettlementRow(settlement, execution.requesterId)
            FROM ExecutionSettlementEntity settlement
            JOIN ExecutionEntity execution ON execution.id = settlement.executionId
            WHERE settlement.supplierId = :supplierId
              AND (:agentId IS NULL OR settlement.agentId = :agentId)
              AND (:startAtInclusive IS NULL OR settlement.createdAt >= :startAtInclusive)
              AND (:endAtExclusive IS NULL OR settlement.createdAt < :endAtExclusive)
            ORDER BY settlement.createdAt DESC, settlement.id DESC
            """)
    List<ExecutionSettlementRow> searchBySupplier(
            @Param("supplierId") Long supplierId,
            @Param("agentId") UUID agentId,
            @Param("startAtInclusive") Instant startAtInclusive,
            @Param("endAtExclusive") Instant endAtExclusive,
            Pageable pageable
    );
}
