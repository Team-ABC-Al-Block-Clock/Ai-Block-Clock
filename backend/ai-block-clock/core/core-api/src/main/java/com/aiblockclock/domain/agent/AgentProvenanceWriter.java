package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentProvenanceRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class AgentProvenanceWriter {
    private final AgentProvenanceRepository agentProvenanceRepository;
    private final Clock clock;

    // 버전을 공개하는 트랜잭션 안에서만 만든다. 공개가 되돌려지면 기록 요청도 함께 사라져, 없는 버전을 체인에 적지 않는다.
    // 체인에는 여기서 보내지 않는다 — 외부 호출을 공개 트랜잭션에 넣지 않는다 (ARCHITECTURE 4.2). 주기 작업이 커밋된 것을 집어 보낸다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void request(AgentVersion published) {
        agentProvenanceRepository.save(
                AgentProvenanceEntityConverter.toEntity(AgentProvenance.request(published, clock.instant())));
    }

    @Transactional
    public void markAttempted(AgentVersionId versionId) {
        agentProvenanceRepository.markAttempted(versionId.value(), clock.instant());
    }

    @Transactional
    public void confirm(AgentVersionId versionId, AgentVersionMetadata metadata, String metadataHash,
                        AgentProvenanceReceipt receipt) {
        agentProvenanceRepository.confirm(versionId.value(), metadata.ownerWalletAddress(), metadataHash,
                metadata.documentUri(), receipt.contractAddress(), receipt.transactionHash(), receipt.blockNumber(),
                clock.instant());
    }
}
