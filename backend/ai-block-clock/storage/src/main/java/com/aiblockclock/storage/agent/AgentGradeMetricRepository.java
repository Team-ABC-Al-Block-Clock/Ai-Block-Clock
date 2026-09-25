package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AgentGradeMetricRepository extends JpaRepository<AgentGradeMetricEntity, UUID> {

    // 줄이 없으면 만들고 있으면 더한다. 읽고 고쳐 쓰지 않고 한 문장으로 더해서, 두 실행이 같은 에이전트를
    // 동시에 세도 한쪽이 다른 쪽을 덮어 하나를 잃지 않는다. 처음 세는 에이전트가 동시에 와도 한 줄만 생긴다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO agent_grade_metric (agent_id, successful_runs, supplier_failures, review_count, measured_at)
            VALUES (:agentId, :successes, :failures, 0, :measuredAt)
            ON DUPLICATE KEY UPDATE
                successful_runs = successful_runs + :successes,
                supplier_failures = supplier_failures + :failures,
                measured_at = :measuredAt
            """, nativeQuery = true)
    int addRuns(
            @Param("agentId") String agentId,
            @Param("successes") long successes,
            @Param("failures") long failures,
            @Param("measuredAt") Instant measuredAt
    );
}
