package com.aiblockclock.storage.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {
    Optional<RefreshTokenEntity> findByRefreshTokenHash(String refreshTokenHash);

    // 일괄 수정은 JPA auditing과 @Version 자동 증가를 거치지 않으므로 함께 갱신한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshTokenEntity token
               SET token.usedAt = :usedAt,
                   token.updatedAt = :usedAt,
                   token.version = token.version + 1
             WHERE token.id = :refreshTokenId
               AND token.sessionId = :sessionId
               AND token.usedAt IS NULL
               AND token.expiresAt > :usedAt
            """)
    int updateUsedAtIfUsable(
            @Param("refreshTokenId") Long refreshTokenId,
            @Param("sessionId") Long sessionId,
            @Param("usedAt") Instant usedAt
    );
}
