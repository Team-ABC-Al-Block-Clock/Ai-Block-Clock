package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    Optional<ExecutionEntity> findByRequesterIdAndRequestId(Long requesterId, UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT execution FROM ExecutionEntity execution WHERE execution.id = :id")
    Optional<ExecutionEntity> findForUpdate(@Param("id") UUID id);

    // 끝나지 않은 실행이다. 서버를 다시 띄우면 이것들을 다시 따라간다.
    List<ExecutionEntity> findAllByStatusIn(Collection<ExecutionStatus> statuses);

    // 실행을 실패로 닫을 때 적은 판정이다. 실패로 닫지 않았거나 판정을 적기 전의 옛 실행은 비어 있다.
    @Query("SELECT failure FROM ExecutionFailureEntity failure WHERE failure.executionId = :id")
    Optional<ExecutionFailureEntity> findFailureById(@Param("id") UUID id);

    // 닫혔는데 정산 근거가 아직 없는 실행이다. 근거는 닫힌 뒤 따로 적으므로, 적다 실패했거나 그 사이 서버가 내려가면 남는다.
    @Query("""
            SELECT execution
            FROM ExecutionEntity execution
            WHERE execution.status IN (com.aiblockclock.enums.execution.ExecutionStatus.SUCCEEDED,
                                       com.aiblockclock.enums.execution.ExecutionStatus.FAILED)
              AND execution.updatedAt >= :since
              AND NOT EXISTS (SELECT 1 FROM ExecutionRunRecordEntity record WHERE record.executionId = execution.id)
            ORDER BY execution.updatedAt ASC
            """)
    List<ExecutionEntity> findClosedWithoutRunRecord(@Param("since") Instant since, Pageable pageable);
}
