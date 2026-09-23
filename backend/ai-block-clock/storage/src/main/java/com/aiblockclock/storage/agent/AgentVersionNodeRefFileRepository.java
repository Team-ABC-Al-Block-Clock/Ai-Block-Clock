package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentVersionNodeRefFileRepository extends JpaRepository<AgentVersionNodeRefFileEntity, UUID> {
    List<AgentVersionNodeRefFileEntity> findByVersionIdOrderByNodeKeyAscOrdinalAsc(UUID versionId);
}
