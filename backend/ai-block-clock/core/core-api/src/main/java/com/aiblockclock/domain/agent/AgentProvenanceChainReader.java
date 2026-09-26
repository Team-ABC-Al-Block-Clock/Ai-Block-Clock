package com.aiblockclock.domain.agent;

import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.SsafyStoDocumentClient;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Optional;

// 확정된 원본 기록을 체인에서 다시 읽는다. 우리 DB 가 아니라 체인이 무엇이라고 말하는지를 보여 주려는 것이다.
@Component
@RequiredArgsConstructor
public class AgentProvenanceChainReader {
    private final SsafyStoDocumentClient ssafyStoDocumentClient;

    // 문서가 없으면 빈 값이다(체인에 없는 기록 = 대조 불일치). 체인에 닿지 못하면 불일치로 보여 주지 않고 실패로 알린다
    public Optional<OnChainAgentProvenance> find(AgentProvenance confirmed) {
        try {
            return ssafyStoDocumentClient.findDocument(confirmed.getContractAddress(), confirmed.getDocumentName())
                    .map(OnChainAgentProvenance::from);
        } catch (BlockchainClientException exception) {
            throw new CoreException(ErrorType.AGENT_PROVENANCE_CHAIN_UNREACHABLE);
        }
    }

    public String documentUrl(AgentProvenance confirmed) {
        return ssafyStoDocumentClient.documentUrl(confirmed.getContractAddress(), confirmed.getDocumentName())
                .toString();
    }
}
