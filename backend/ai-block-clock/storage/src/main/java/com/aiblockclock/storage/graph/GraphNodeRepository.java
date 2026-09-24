package com.aiblockclock.storage.graph;

import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GraphNodeRepository extends JpaRepository<GraphNodeEntity, Long> {
    List<GraphNodeEntity> findByGraphIdOrderByIdAsc(UUID graphId);

    boolean existsByReferenceId(UUID referenceId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update GraphNodeEntity n
               set n.sourceType = :sourceType,
                   n.referenceId = :referenceId,
                   n.versionId = :versionId,
                   n.inputSource = :inputSource,
                   n.controlType = :controlType,
                   n.branchCondition = :branchCondition
             where n.graphId = :graphId
               and n.nodeKey = :nodeKey
            """)
    int updatePlacement(@Param("graphId") UUID graphId,
                        @Param("nodeKey") String nodeKey,
                        @Param("sourceType") SourceType sourceType,
                        @Param("referenceId") UUID referenceId,
                        @Param("versionId") UUID versionId,
                        @Param("inputSource") InputSource inputSource,
                        @Param("controlType") ControlType controlType,
                        @Param("branchCondition") String branchCondition);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GraphNodeEntity n
             where n.graphId = :graphId
               and n.nodeKey not in :keptKeys
            """)
    int deleteMissing(@Param("graphId") UUID graphId, @Param("keptKeys") Collection<String> keptKeys);

    // 자식 행은 같은 트랜잭션에서 다시 읽지 않는다. 그래서 셋 다 flushAutomatically 만 둔다.
    @Modifying(flushAutomatically = true)
    @Query("delete from GraphNodeEntity n where n.graphId = :graphId")
    int deleteAllByGraphId(@Param("graphId") UUID graphId);
}
