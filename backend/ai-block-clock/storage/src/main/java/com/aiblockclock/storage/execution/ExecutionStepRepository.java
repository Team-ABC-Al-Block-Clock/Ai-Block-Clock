package com.aiblockclock.storage.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionStepRepository extends JpaRepository<ExecutionStepEntity, Long> {
    List<ExecutionStepEntity> findAllByExecutionIdOrderByIdAsc(UUID executionId);
}
