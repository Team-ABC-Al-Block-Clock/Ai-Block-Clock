package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.AgentProvenanceStatus;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

// 공개된 에이전트 버전 하나의 원본 기록이다. 공개할 때 PENDING 으로 생기고, 체인에 적힌 것을 확인하면 CONFIRMED 가 된다.
// 원작자 · 해시 · 거래 칸은 확정된 뒤에만 있다. 적히기 전에 계산한 값을 들고 있지 않아, 적힌 값과 어긋날 일이 없다.
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentProvenance {
    private final AgentVersionId versionId;
    private final AgentId agentId;
    private final VersionNumber versionNumber;
    private final String documentName;
    private final AgentProvenanceStatus status;
    private final Instant requestedAt;
    private final String ownerWalletAddress;
    private final String metadataHash;
    private final String documentUri;
    private final String contractAddress;
    private final String transactionHash;
    private final Long blockNumber;
    private final Instant confirmedAt;

    public static AgentProvenance request(AgentVersion version, Instant requestedAt) {
        return new AgentProvenance(version.getId(), version.getAgentId(), version.getNumber(),
                documentNameOf(version.getAgentId(), version.getNumber()), AgentProvenanceStatus.PENDING,
                requestedAt, null, null, null, null, null, null, null);
    }

    public static AgentProvenance restore(
            AgentVersionId versionId,
            AgentId agentId,
            VersionNumber versionNumber,
            String documentName,
            AgentProvenanceStatus status,
            Instant requestedAt,
            String ownerWalletAddress,
            String metadataHash,
            String documentUri,
            String contractAddress,
            String transactionHash,
            Long blockNumber,
            Instant confirmedAt
    ) {
        return new AgentProvenance(versionId, agentId, versionNumber, documentName, status, requestedAt,
                ownerWalletAddress, metadataHash, documentUri, contractAddress, transactionHash, blockNumber,
                confirmedAt);
    }

    public boolean isConfirmed() {
        return status == AgentProvenanceStatus.CONFIRMED;
    }

    // 체인 쪽 열쇠다. ERC-1400 문서 이름(bytes32)에 들어가도록 31자 안에서 에이전트와 버전을 가린다.
    // 에이전트 ID 16바이트를 URL 에 써도 되는 Base64 로 줄여 22자, 뒤에 "-v버전" 을 붙인다. 예: EjRWeJASNFZ4kBI0VniQEg-v1
    static String documentNameOf(AgentId agentId, VersionNumber versionNumber) {
        UUID id = agentId.value();
        byte[] bytes = ByteBuffer.allocate(16)
                .putLong(id.getMostSignificantBits())
                .putLong(id.getLeastSignificantBits())
                .array();

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "-v" + versionNumber.value();
    }
}
