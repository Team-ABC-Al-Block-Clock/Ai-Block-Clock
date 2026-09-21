package com.aiblockclock.storage.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdgeEntity, Long> {
    List<WorkflowEdgeEntity> findByWorkflowIdOrderByIdAsc(UUID workflowId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update WorkflowEdgeEntity e
               set e.sourceNodeKey = :sourceNodeKey,
                   e.targetNodeKey = :targetNodeKey
             where e.workflowId = :workflowId
               and e.edgeKey = :edgeKey
            """)
    int updateEnds(@Param("workflowId") UUID workflowId,
                   @Param("edgeKey") String edgeKey,
                   @Param("sourceNodeKey") String sourceNodeKey,
                   @Param("targetNodeKey") String targetNodeKey);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from WorkflowEdgeEntity e
             where e.workflowId = :workflowId
               and e.edgeKey not in :keptKeys
            """)
    int deleteMissing(@Param("workflowId") UUID workflowId, @Param("keptKeys") Collection<String> keptKeys);

    // 자식 행은 같은 트랜잭션에서 다시 읽지 않는다. 그래서 셋 다 flushAutomatically 만 둔다.
    @Modifying(flushAutomatically = true)
    @Query("delete from WorkflowEdgeEntity e where e.workflowId = :workflowId")
    int deleteAllByWorkflowId(@Param("workflowId") UUID workflowId);
}
