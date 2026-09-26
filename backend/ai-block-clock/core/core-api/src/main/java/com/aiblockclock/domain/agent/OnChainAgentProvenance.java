package com.aiblockclock.domain.agent;

import com.aiblockclock.client.blockchain.ChainDocument;

import java.util.Locale;

// 체인의 등기부에서 읽은 원본 기록이다. 문서 주소 칸에서 원작자 지갑을 꺼낸다 (AgentVersionMetadata.documentUri).
// 주소 칸의 모양이 다르면 원작자를 알 수 없는 것으로 두어, 대조에서 일치하지 않게 한다.
public record OnChainAgentProvenance(
        String ownerWalletAddress,
        String metadataHash
) {
    private static final String OWNER_PARAMETER = "?owner=";

    public static OnChainAgentProvenance from(ChainDocument document) {
        int ownerAt = document.uri().indexOf(OWNER_PARAMETER);
        String owner = ownerAt < 0 ? null : document.uri().substring(ownerAt + OWNER_PARAMETER.length());

        return new OnChainAgentProvenance(owner, document.hash().toLowerCase(Locale.ROOT));
    }
}
