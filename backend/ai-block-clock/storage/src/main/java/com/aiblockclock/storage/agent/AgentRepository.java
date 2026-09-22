package com.aiblockclock.storage.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<AgentEntity, UUID> {
    boolean existsByIdAndOwnerId(UUID id, Long ownerId);

    @Query("select a.publicVersionId from AgentEntity a where a.id = :id and a.publicVersionId is not null")
    Optional<UUID> findPublicVersionId(@Param("id") UUID id);

    @Query("""
            select new com.aiblockclock.storage.agent.PublicAgentRow(
                a.id, a.ownerId, a.name, a.summary, v.tier, v.baseCost)
            from AgentEntity a, AgentVersionEntity v
            where v.id = a.publicVersionId
            order by a.createdAt desc, a.id desc
            """)
    List<PublicAgentRow> findPublic();

    @Query("""
            select new com.aiblockclock.storage.agent.PublicAgentRow(
                a.id, a.ownerId, a.name, a.summary, v.tier, v.baseCost)
            from AgentEntity a, AgentVersionEntity v
            where v.id = a.publicVersionId and a.id in :ids
            """)
    List<PublicAgentRow> findPublicByIds(@Param("ids") Collection<UUID> ids);
}
