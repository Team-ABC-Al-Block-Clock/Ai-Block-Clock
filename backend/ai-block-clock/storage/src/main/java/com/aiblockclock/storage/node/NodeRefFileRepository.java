package com.aiblockclock.storage.node;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NodeRefFileRepository extends JpaRepository<NodeRefFileEntity, UUID> {
    List<NodeRefFileEntity> findByNodeDefinitionIdOrderByOrdinal(UUID nodeDefinitionId);

    List<NodeRefFileEntity> findByNodeDefinitionIdInOrderByNodeDefinitionIdAscOrdinalAsc(
            List<UUID> nodeDefinitionIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from NodeRefFileEntity f where f.nodeDefinitionId = :nodeDefinitionId")
    int deleteAllByNodeDefinitionId(@Param("nodeDefinitionId") UUID nodeDefinitionId);
}
