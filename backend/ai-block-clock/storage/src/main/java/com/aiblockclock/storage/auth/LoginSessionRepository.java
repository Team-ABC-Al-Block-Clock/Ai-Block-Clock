package com.aiblockclock.storage.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSessionEntity, Long> {
}
