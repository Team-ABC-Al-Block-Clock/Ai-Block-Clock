package com.aiblockclock.storage.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WorkflowEdgeRepository extends JpaRepository<WorkflowEdgeEntity, Long> {
    List<WorkflowEdgeEntity> findByWorkflowIdOrderByIdAsc(UUID workflowId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkflowEdgeEntity e where e.workflowId = :workflowId")
    int deleteAllByWorkflowId(@Param("workflowId") UUID workflowId);
}
