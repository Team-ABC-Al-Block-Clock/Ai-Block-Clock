package com.aiblockclock.storage.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentVersionRepository extends JpaRepository<AgentVersionEntity, UUID> {
    @Query("""
            select v from AgentVersionEntity v
            join fetch v.agent a
            where a.publicVersionId = v.id
            order by a.createdAt desc, a.id desc
            """)
    List<AgentVersionEntity> findPublic();

    @Query("""
            select v from AgentVersionEntity v
            join fetch v.agent a
            where a.publicVersionId = v.id and a.id in :ids
            """)
    List<AgentVersionEntity> findPublicByAgentIds(@Param("ids") Collection<UUID> ids);

    @Query("""
            select v from AgentVersionEntity v
            join fetch v.agent a
            where a.publicVersionId = v.id
              and (:keyword is null
                or a.name like concat('%', :keyword, '%')
                or a.summary like concat('%', :keyword, '%'))
            order by a.createdAt desc, a.id desc
            """)
    List<AgentVersionEntity> findPublicOrderByLatest(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select v from AgentVersionEntity v
            join fetch v.agent a
            where a.publicVersionId = v.id and a.id = :agentId
            """)
    Optional<AgentVersionEntity> findPublicByAgentId(@Param("agentId") UUID agentId);

    @Query("""
            select v from AgentVersionEntity v
            join fetch v.agent a
            where a.publicVersionId = v.id and a.ownerId = :ownerId
            order by a.createdAt desc, a.id desc
            """)
    List<AgentVersionEntity> findPublicByOwnerId(@Param("ownerId") Long ownerId);

    List<AgentVersionEntity> findAllByAgentIdOrderByVersionNoAsc(UUID agentId);
}
