package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {
    boolean existsByIdAndOwnerId(UUID id, Long ownerId);
}
