package com.aiblockclock.domain.agent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.Clock;

// 확정된 원본 기록을 지금 내용 · 체인과 대조하고, 결과를 기록 줄에 남긴다 (CHN-003).
// 체인을 읽으므로 트랜잭션을 열지 않는다. 결과를 남기는 쓰기만 Writer 가 짧게 한다 (ARCHITECTURE 4.2).
@Component
@RequiredArgsConstructor
public class AgentIntegrityVerifier {
    private final AgentProvenanceChainReader agentProvenanceChainReader;
    private final AgentVersionMetadataReader agentVersionMetadataReader;
    private final AgentProvenanceWriter agentProvenanceWriter;
    private final Clock clock;

    // 체인에 닿지 못하면 AGENT_PROVENANCE_CHAIN_UNREACHABLE 로 끝나고 아무것도 남기지 않는다. 모르는 것을 불일치로 적지 않는다.
    // 내용은 원본 기록을 보낼 때와 같은 Reader · 같은 해시 규칙으로 다시 모은다. 규칙이 다르면 정상도 불일치로 보인다
    public AgentIntegrity verify(AgentProvenance confirmed) {
        OnChainAgentProvenance onChain = agentProvenanceChainReader.find(confirmed).orElse(null);
        AgentVersionMetadata current = agentVersionMetadataReader.read(confirmed.getVersionId());
        AgentIntegrity integrity = AgentIntegrity.check(confirmed, current, onChain, clock.instant());

        agentProvenanceWriter.recordIntegrity(confirmed.getVersionId(), integrity);

        return integrity;
    }
}
