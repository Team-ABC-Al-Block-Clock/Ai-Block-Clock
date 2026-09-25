package com.aiblockclock.storage.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ExecutionFailureRepository extends JpaRepository<ExecutionFailureEntity, UUID> {
}
