package com.aiblockclock.storage.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GraphRepository extends JpaRepository<GraphEntity, UUID> {
}
