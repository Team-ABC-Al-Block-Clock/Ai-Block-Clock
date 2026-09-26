package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentGradeHistoryRepository extends JpaRepository<AgentGradeHistoryEntity, Long> {
    Optional<AgentGradeHistoryEntity> findFirstByAgentIdOrderByIdDesc(UUID agentId);
}
