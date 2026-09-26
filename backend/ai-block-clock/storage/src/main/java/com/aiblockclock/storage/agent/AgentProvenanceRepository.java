package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentIntegrityResult;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgentProvenanceRepository extends JpaRepository<AgentProvenanceEntity, UUID> {

    // 아직 적히지 않은 기록을, 마지막으로 손댄 때가 오래된 것부터 고른다. 한 번도 보내지 않은 것은 요청한 때로 잰다
    @Query("""
            select p.agentVersionId from AgentProvenanceEntity p
            where p.status = com.aiblockclock.enums.agent.AgentProvenanceStatus.PENDING
            order by coalesce(p.lastAttemptedAt, p.requestedAt) asc, p.requestedAt asc
            """)
    List<UUID> findPendingIds(Pageable pageable);

    Optional<AgentProvenanceEntity> findFirstByAgentIdOrderByVersionNoAsc(UUID agentId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update AgentProvenanceEntity p set p.lastAttemptedAt = :attemptedAt
            where p.agentVersionId = :agentVersionId
              and p.status = com.aiblockclock.enums.agent.AgentProvenanceStatus.PENDING
            """)
    int markAttempted(@Param("agentVersionId") UUID agentVersionId, @Param("attemptedAt") Instant attemptedAt);

    // PENDING 인 것만 바꾼다. 같은 기록을 두 번 확정해도 먼저 적힌 거래 해시가 덮이지 않는다
    @Modifying(flushAutomatically = true)
    @Query("""
            update AgentProvenanceEntity p
            set p.status = com.aiblockclock.enums.agent.AgentProvenanceStatus.CONFIRMED,
                p.ownerWalletAddress = :ownerWalletAddress,
                p.metadataHash = :metadataHash,
                p.documentUri = :documentUri,
                p.contractAddress = :contractAddress,
                p.transactionHash = :transactionHash,
                p.blockNumber = :blockNumber,
                p.confirmedAt = :confirmedAt,
                p.lastAttemptedAt = :confirmedAt
            where p.agentVersionId = :agentVersionId
              and p.status = com.aiblockclock.enums.agent.AgentProvenanceStatus.PENDING
            """)
    int confirm(
            @Param("agentVersionId") UUID agentVersionId,
            @Param("ownerWalletAddress") String ownerWalletAddress,
            @Param("metadataHash") String metadataHash,
            @Param("documentUri") String documentUri,
            @Param("contractAddress") String contractAddress,
            @Param("transactionHash") String transactionHash,
            @Param("blockNumber") Long blockNumber,
            @Param("confirmedAt") Instant confirmedAt
    );

    // 확정된 기록에만 남긴다. 대조는 체인에 적힌 값이 있어야 하는 일이라 PENDING 에는 결과가 있을 수 없다
    @Modifying(flushAutomatically = true)
    @Query("""
            update AgentProvenanceEntity p
            set p.integrityResult = :result,
                p.integrityMismatchReason = :mismatchReason,
                p.integrityCheckedAt = :checkedAt
            where p.agentVersionId = :agentVersionId
              and p.status = com.aiblockclock.enums.agent.AgentProvenanceStatus.CONFIRMED
            """)
    int recordIntegrity(
            @Param("agentVersionId") UUID agentVersionId,
            @Param("result") AgentIntegrityResult result,
            @Param("mismatchReason") AgentIntegrityMismatchReason mismatchReason,
            @Param("checkedAt") Instant checkedAt
    );
}
