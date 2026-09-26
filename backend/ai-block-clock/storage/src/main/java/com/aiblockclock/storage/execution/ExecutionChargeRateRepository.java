package com.aiblockclock.storage.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExecutionChargeRateRepository extends JpaRepository<ExecutionChargeRateEntity, Long> {
    List<ExecutionChargeRateEntity> findAllByExecutionId(UUID executionId);
}
