package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentVersionRepository extends JpaRepository<AgentVersionEntity, UUID> {
}
