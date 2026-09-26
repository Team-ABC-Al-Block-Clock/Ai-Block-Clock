package com.aiblockclock.storage.node;

import com.aiblockclock.enums.node.NodeTestRunStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeTestRunRepository extends JpaRepository<NodeTestRunEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from NodeTestRunEntity r where r.id = :id")
    Optional<NodeTestRunEntity> findByIdForUpdate(@Param("id") UUID id);

    long countByNodeIdAndRequestedAtGreaterThanEqual(UUID nodeId, Instant since);

    // 모델을 실제로 불러 원가가 잰 성공 기록만 본다. mock 은 원가가 0 이라 뺀다.
    @Query("""
            select r.costUsd from NodeTestRunEntity r
            where r.nodeId = :nodeId
              and r.status = :status
              and r.mock = false
              and r.costUsd > 0
            order by r.finishedAt desc, r.id desc
            """)
    List<BigDecimal> findRecentCosts(@Param("nodeId") UUID nodeId,
                                     @Param("status") NodeTestRunStatus status,
                                     Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from NodeTestRunEntity r where r.nodeId = :nodeId")
    void deleteAllByNodeId(@Param("nodeId") UUID nodeId);
}
