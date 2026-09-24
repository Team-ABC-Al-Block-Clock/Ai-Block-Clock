package com.aiblockclock.storage.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    Optional<ExecutionEntity> findByRequesterIdAndRequestId(Long requesterId, UUID requestId);
}
