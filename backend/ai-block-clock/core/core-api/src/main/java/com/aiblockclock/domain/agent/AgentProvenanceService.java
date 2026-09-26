package com.aiblockclock.domain.agent;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

// 공개할 때 쌓인 원본 기록(PENDING)을 한 건씩 체인에 적고 확정한다 (CHN-001).
// 체인 호출은 트랜잭션 밖에서 하고, 결과를 적는 쓰기만 각자 짧은 트랜잭션으로 한다 (ARCHITECTURE 4.2).
@Service
@RequiredArgsConstructor
public class AgentProvenanceService {
    private final AgentProvenanceReader agentProvenanceReader;
    private final AgentVersionMetadataReader agentVersionMetadataReader;
    private final AgentProvenanceSender agentProvenanceSender;
    private final AgentProvenanceWriter agentProvenanceWriter;

    public void ensureRegistrationReady() {
        agentProvenanceSender.ensureReady();
    }

    public List<AgentVersionId> readPendingVersionIds(int limit) {
        return agentProvenanceReader.readPendingIds(limit);
    }

    // 적혔으면 true, 이번 차례에 적지 못했으면 false 다. 적지 못한 기록은 손댄 시각을 남겨 다음 차례의 뒤로 보낸다.
    // 내용을 읽다 깨진 경우(예외)도 뒤로 보낸다. 그러지 않으면 그 한 건이 매 차례 맨 앞에 서서 나머지를 막는다
    public boolean register(AgentVersionId versionId) {
        AgentProvenance provenance = agentProvenanceReader.read(versionId);

        if (provenance.isConfirmed()) {
            return true;
        }

        boolean registered = false;

        try {
            registered = sendAndConfirm(provenance);
        } finally {
            if (!registered) {
                agentProvenanceWriter.markAttempted(versionId);
            }
        }

        return registered;
    }

    // 체인에 적힌 뒤 확정 쓰기가 실패해도 다음 차례에 같은 값을 다시 적고 확정한다. 다시 적어도 달라지는 것이 없다
    private boolean sendAndConfirm(AgentProvenance provenance) {
        AgentVersionMetadata metadata = agentVersionMetadataReader.read(provenance.getVersionId());
        String metadataHash = metadata.hash();
        Optional<AgentProvenanceReceipt> receipt = agentProvenanceSender.send(
                provenance.getDocumentName(), metadata.documentUri(), metadataHash);

        if (receipt.isEmpty()) {
            return false;
        }

        agentProvenanceWriter.confirm(provenance.getVersionId(), metadata, metadataHash, receipt.get());

        return true;
    }
}
