package com.aiblockclock.storage.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface WalletAuthMessageRepository extends JpaRepository<WalletAuthMessageEntity, Long> {
    Optional<WalletAuthMessageEntity> findByWalletAddress(String walletAddress);

    // 일괄 수정은 JPA auditing과 @Version 자동 증가를 거치지 않으므로 함께 갱신한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE WalletAuthMessageEntity authMessage
               SET authMessage.usedAt = :usedAt,
                   authMessage.updatedAt = :usedAt,
                   authMessage.version = authMessage.version + 1
             WHERE authMessage.walletAddress = :walletAddress
               AND authMessage.nonce = :expectedNonce
               AND authMessage.usedAt IS NULL
               AND authMessage.expiresAt > :usedAt
            """)
    int updateUsedAtIfUsable(
            @Param("walletAddress") String walletAddress,
            @Param("expectedNonce") String expectedNonce,
            @Param("usedAt") Instant usedAt
    );
}
