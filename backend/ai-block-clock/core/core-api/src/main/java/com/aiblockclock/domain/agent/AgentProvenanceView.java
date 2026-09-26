package com.aiblockclock.domain.agent;

import java.time.Instant;

// 에이전트 하나의 원본 증명이다. 지금 공개된 버전의 기록을 보여 주고, 등록 시각만 처음 공개한 버전의 것을 쓴다.
// offChain 은 우리 DB 가, onChain 은 체인이 말하는 값이다. 확정 전(PENDING)에는 체인 쪽 · 거래 칸이 비어 있다.
public record AgentProvenanceView(
        AgentId agentId,
        String ownerNickname,
        String offChainOwnerAddress,
        AgentProvenance current,
        Instant registeredAt,
        String explorerUrl,
        OnChainAgentProvenance onChain,
        long chainId,
        String networkName
) {
    public boolean ownerMatched() {
        return onChain != null && onChain.ownerWalletAddress() != null
                && onChain.ownerWalletAddress().equalsIgnoreCase(offChainOwnerAddress);
    }

    public boolean metadataMatched() {
        return onChain != null && current.getMetadataHash() != null
                && current.getMetadataHash().equalsIgnoreCase(onChain.metadataHash());
    }

    // 블록에 들어가기 전의 기록은 되돌려질 수 있어 검증됐다고 하지 않는다
    public boolean verified() {
        return current.isConfirmed() && ownerMatched() && metadataMatched();
    }
}
