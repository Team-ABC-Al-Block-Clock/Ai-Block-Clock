package com.aiblockclock.storage.execution;

import com.aiblockclock.enums.execution.ExecutionResultFormat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExecutionResultRepository extends JpaRepository<ExecutionResultEntity, Long> {
    Optional<ExecutionResultEntity> findByExecutionIdAndFormat(UUID executionId, ExecutionResultFormat format);

    List<ExecutionResultEntity> findByExecutionIdOrderByIdAsc(UUID executionId);

    boolean existsByExecutionId(UUID executionId);
}
