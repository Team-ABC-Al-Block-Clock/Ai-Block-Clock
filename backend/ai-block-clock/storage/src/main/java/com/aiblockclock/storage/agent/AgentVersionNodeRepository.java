package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentVersionNodeRepository extends JpaRepository<AgentVersionNodeEntity, Long> {
    List<AgentVersionNodeEntity> findByAgentVersionId(UUID agentVersionId);
}
