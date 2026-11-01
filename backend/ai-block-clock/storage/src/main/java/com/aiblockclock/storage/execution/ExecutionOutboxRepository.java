package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ExecutionOutboxRepository extends JpaRepository<ExecutionOutboxEntity, UUID> {

    // 아직 넘기지 않은 줄이다. 실행도 넘긴 기록(run_id)이 없고 statuses(끝나지 않은 상태) 중 하나여야 한다 —
    // 넘기지 못한 채 버려진 실행의 줄은 dispatched_at 이 빈 채로 남으므로 실행 쪽 상태로 거른다. 접수한 순서대로다.
    @Query("""
            SELECT outbox
            FROM ExecutionOutboxEntity outbox
            WHERE outbox.dispatchedAt IS NULL
              AND EXISTS (SELECT 1 FROM ExecutionEntity execution
                          WHERE execution.id = outbox.executionId
                            AND execution.runId IS NULL
                            AND execution.status IN :statuses)
            ORDER BY outbox.createdAt ASC
            """)
    List<ExecutionOutboxEntity> findUndispatched(@Param("statuses") Collection<ExecutionStatus> statuses);
}
