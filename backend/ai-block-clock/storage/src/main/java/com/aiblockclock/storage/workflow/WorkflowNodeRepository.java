package com.aiblockclock.storage.workflow;

import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkflowNodeRepository extends JpaRepository<WorkflowNodeEntity, Long> {
    List<WorkflowNodeEntity> findByWorkflowIdOrderByIdAsc(UUID workflowId);

    boolean existsByReferenceId(UUID referenceId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update WorkflowNodeEntity n
               set n.sourceType = :sourceType,
                   n.referenceId = :referenceId,
                   n.versionId = :versionId,
                   n.inputSource = :inputSource
             where n.workflowId = :workflowId
               and n.nodeKey = :nodeKey
            """)
    int updatePlacement(@Param("workflowId") UUID workflowId,
                        @Param("nodeKey") String nodeKey,
                        @Param("sourceType") SourceType sourceType,
                        @Param("referenceId") UUID referenceId,
                        @Param("versionId") UUID versionId,
                        @Param("inputSource") InputSource inputSource);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from WorkflowNodeEntity n
             where n.workflowId = :workflowId
               and n.nodeKey not in :keptKeys
            """)
    int deleteMissing(@Param("workflowId") UUID workflowId, @Param("keptKeys") Collection<String> keptKeys);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from WorkflowNodeEntity n where n.workflowId = :workflowId")
    int deleteAllByWorkflowId(@Param("workflowId") UUID workflowId);
}
