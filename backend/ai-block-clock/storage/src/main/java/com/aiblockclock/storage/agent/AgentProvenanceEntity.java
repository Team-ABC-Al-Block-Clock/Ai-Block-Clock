package com.aiblockclock.storage.agent;

import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentIntegrityResult;
import com.aiblockclock.enums.agent.AgentProvenanceStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

// 만들 때는 요청 칸만 채운다. 체인에 적힌 뒤의 칸은 리포지토리의 조건부 UPDATE(confirm)로 한 번에 채운다.
// 대조 결과 칸(integrity_*)은 확정된 기록을 조회할 때마다 recordIntegrity 가 덮어쓴다.
@Entity
@Table(name = "agent_provenance")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentProvenanceEntity {
    @Id
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_version_id", length = 36, nullable = false, updatable = false)
    private UUID agentVersionId;

    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "agent_id", length = 36, nullable = false, updatable = false)
    private UUID agentId;

    @Column(name = "version_no", nullable = false, updatable = false)
    private Integer versionNo;

    @Column(name = "document_name", length = 31, nullable = false, updatable = false)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AgentProvenanceStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "last_attempted_at")
    private Instant lastAttemptedAt;

    @Column(name = "owner_wallet_address", length = 42)
    private String ownerWalletAddress;

    @Column(name = "metadata_hash", length = 66)
    private String metadataHash;

    @Column(name = "document_uri", length = 200)
    private String documentUri;

    @Column(name = "contract_address", length = 42)
    private String contractAddress;

    @Column(name = "transaction_hash", length = 66)
    private String transactionHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_result", length = 20)
    private AgentIntegrityResult integrityResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_mismatch_reason", length = 30)
    private AgentIntegrityMismatchReason integrityMismatchReason;

    @Column(name = "integrity_checked_at")
    private Instant integrityCheckedAt;

    public AgentProvenanceEntity(UUID agentVersionId, UUID agentId, Integer versionNo, String documentName,
                                 Instant requestedAt) {
        this.agentVersionId = agentVersionId;
        this.agentId = agentId;
        this.versionNo = versionNo;
        this.documentName = documentName;
        this.status = AgentProvenanceStatus.PENDING;
        this.requestedAt = requestedAt;
    }
}
