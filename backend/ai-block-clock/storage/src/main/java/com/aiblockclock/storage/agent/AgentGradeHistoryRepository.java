package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentGradeHistoryRepository extends JpaRepository<AgentGradeHistoryEntity, Long> {
}
