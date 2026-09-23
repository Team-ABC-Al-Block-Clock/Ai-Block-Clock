package com.aiblockclock.storage.node;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NodeRefFileRepository extends JpaRepository<NodeRefFileEntity, UUID> {
    List<NodeRefFileEntity> findByNodeDefinitionIdOrderByOrdinal(UUID nodeDefinitionId);

    List<NodeRefFileEntity> findByNodeDefinitionIdInOrderByNodeDefinitionIdAscOrdinalAsc(
            List<UUID> nodeDefinitionIds);
}
