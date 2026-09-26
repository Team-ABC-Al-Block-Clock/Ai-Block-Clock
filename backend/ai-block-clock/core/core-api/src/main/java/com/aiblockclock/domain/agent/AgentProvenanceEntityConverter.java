package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentProvenanceEntity;

final class AgentProvenanceEntityConverter {
    private AgentProvenanceEntityConverter() {
    }

    static AgentProvenanceEntity toEntity(AgentProvenance requested) {
        return new AgentProvenanceEntity(requested.getVersionId().value(), requested.getAgentId().value(),
                requested.getVersionNumber().value(), requested.getDocumentName(), requested.getRequestedAt());
    }

    static AgentProvenance toDomain(AgentProvenanceEntity entity) {
        return AgentProvenance.restore(
                AgentVersionId.of(entity.getAgentVersionId()),
                AgentId.of(entity.getAgentId()),
                VersionNumber.of(entity.getVersionNo()),
                entity.getDocumentName(),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getOwnerWalletAddress(),
                entity.getMetadataHash(),
                entity.getDocumentUri(),
                entity.getContractAddress(),
                entity.getTransactionHash(),
                entity.getBlockNumber(),
                entity.getConfirmedAt());
    }
}
