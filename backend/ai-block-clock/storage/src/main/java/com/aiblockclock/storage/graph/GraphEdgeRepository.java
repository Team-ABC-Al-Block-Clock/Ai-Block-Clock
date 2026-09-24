package com.aiblockclock.storage.graph;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GraphEdgeRepository extends JpaRepository<GraphEdgeEntity, Long> {
    List<GraphEdgeEntity> findByGraphIdOrderByIdAsc(UUID graphId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update GraphEdgeEntity e
               set e.sourceNodeKey = :sourceNodeKey,
                   e.targetNodeKey = :targetNodeKey,
                   e.branchWhen = :branchWhen
             where e.graphId = :graphId
               and e.edgeKey = :edgeKey
            """)
    int updateEnds(@Param("graphId") UUID graphId,
                   @Param("edgeKey") String edgeKey,
                   @Param("sourceNodeKey") String sourceNodeKey,
                   @Param("targetNodeKey") String targetNodeKey,
                   @Param("branchWhen") Boolean branchWhen);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GraphEdgeEntity e
             where e.graphId = :graphId
               and e.edgeKey not in :keptKeys
            """)
    int deleteMissing(@Param("graphId") UUID graphId, @Param("keptKeys") Collection<String> keptKeys);

    // 자식 행은 같은 트랜잭션에서 다시 읽지 않는다. 그래서 셋 다 flushAutomatically 만 둔다.
    @Modifying(flushAutomatically = true)
    @Query("delete from GraphEdgeEntity e where e.graphId = :graphId")
    int deleteAllByGraphId(@Param("graphId") UUID graphId);
}
