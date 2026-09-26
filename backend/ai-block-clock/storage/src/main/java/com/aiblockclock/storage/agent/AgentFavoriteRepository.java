package com.aiblockclock.storage.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgentFavoriteRepository extends JpaRepository<AgentFavoriteEntity, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO agent_favorite (user_id, agent_id, created_at)
            VALUES (:userId, :agentId, :createdAt)
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("agentId") String agentId,
            @Param("createdAt") Instant createdAt
    );

    @Modifying(flushAutomatically = true)
    @Query("delete from AgentFavoriteEntity f where f.userId = :userId and f.agentId = :agentId")
    int deleteByUserIdAndAgentId(@Param("userId") Long userId, @Param("agentId") UUID agentId);

    boolean existsByUserIdAndAgentId(Long userId, UUID agentId);

    long countByAgentId(UUID agentId);

    @Query("select f.agentId from AgentFavoriteEntity f where f.userId = :userId and f.agentId in :agentIds")
    List<UUID> findAgentIdsByUserIdAndAgentIdIn(
            @Param("userId") Long userId,
            @Param("agentIds") Collection<UUID> agentIds
    );

    @Query("""
            select f from AgentFavoriteEntity f
            join AgentEntity a on a.id = f.agentId
            where f.userId = :userId and a.publicVersionId is not null
            order by f.createdAt desc, f.id desc
            """)
    List<AgentFavoriteEntity> findPublicByUserId(@Param("userId") Long userId, Pageable pageable);
}
