package com.aiblockclock.storage.execution;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ExecutionRepository extends JpaRepository<ExecutionEntity, UUID> {
    Optional<ExecutionEntity> findByRequesterIdAndRequestId(Long requesterId, UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT execution FROM ExecutionEntity execution WHERE execution.id = :id")
    Optional<ExecutionEntity> findForUpdate(@Param("id") UUID id);
}
