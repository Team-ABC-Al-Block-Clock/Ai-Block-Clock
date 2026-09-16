package com.aiblockclock.storage.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WalletAuthMessageRepository extends JpaRepository<WalletAuthMessageEntity, Long> {
    Optional<WalletAuthMessageEntity> findByWalletAddress(String walletAddress);
}
