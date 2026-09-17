package com.aiblockclock.storage.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LoginSessionRepository extends JpaRepository<LoginSessionEntity, Long> {
    // 일괄 수정은 JPA auditing과 @Version 자동 증가를 거치지 않으므로 함께 갱신한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LoginSessionEntity session
               SET session.revokedAt = :revokedAt,
                   session.updatedAt = :revokedAt,
                   session.version = session.version + 1
             WHERE session.id = :sessionId
               AND session.revokedAt IS NULL
            """)
    int updateRevokedAtIfNotRevoked(
            @Param("sessionId") Long sessionId,
            @Param("revokedAt") Instant revokedAt
    );

    // 새 세션을 만들기 전에 해당 사용자의 미종료 세션들을 종료한다.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE LoginSessionEntity session
               SET session.revokedAt = :revokedAt,
                   session.updatedAt = :revokedAt,
                   session.version = session.version + 1
             WHERE session.userId = :userId
               AND session.revokedAt IS NULL
            """)
    int updateRevokedAtByUserIdIfNotRevoked(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt
    );
}
