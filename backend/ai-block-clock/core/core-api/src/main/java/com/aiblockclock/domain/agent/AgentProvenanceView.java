package com.aiblockclock.domain.agent;

import java.time.Instant;

// 에이전트 하나의 원본 증명이다. 지금 공개된 버전의 기록을 보여 주고, 등록 시각만 처음 공개한 버전의 것을 쓴다.
// offChain 은 우리 DB 가, onChain 은 체인이 말하는 값이다. 확정 전(PENDING)에는 체인 쪽 · 거래 칸이 비어 있다.
// 해시의 offChain 쪽은 적을 때 남긴 값이 아니라 지금 내용으로 다시 만든 값이다 — 공개 뒤에 DB 가 바뀌었는지를 본다 (CHN-003).
public record AgentProvenanceView(
        AgentId agentId,
        String ownerNickname,
        String offChainOwnerAddress,
        AgentProvenance current,
        Instant registeredAt,
        String explorerUrl,
        AgentIntegrity integrity,
        long chainId,
        String networkName
) {
    public OnChainAgentProvenance onChain() {
        return integrity.onChain();
    }

    public boolean ownerMatched() {
        return onChain() != null && onChain().ownerWalletAddress() != null
                && onChain().ownerWalletAddress().equalsIgnoreCase(offChainOwnerAddress);
    }

    public boolean metadataMatched() {
        return onChain() != null && integrity.currentMetadataHash() != null
                && integrity.currentMetadataHash().equalsIgnoreCase(onChain().metadataHash());
    }

    // 블록에 들어가기 전의 기록은 되돌려질 수 있어 검증됐다고 하지 않는다. 확정 전이면 NOT_CONFIRMED 라 여기서 걸린다
    public boolean verified() {
        return integrity.matched();
    }
}
