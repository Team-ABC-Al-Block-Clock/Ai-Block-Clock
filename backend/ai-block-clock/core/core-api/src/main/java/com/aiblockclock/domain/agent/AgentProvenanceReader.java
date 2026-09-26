package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentProvenanceRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentProvenanceReader {
    private final AgentProvenanceRepository agentProvenanceRepository;

    public List<AgentVersionId> readPendingIds(int limit) {
        return agentProvenanceRepository.findPendingIds(PageRequest.of(0, limit)).stream()
                .map(AgentVersionId::of)
                .toList();
    }

    public AgentProvenance read(AgentVersionId versionId) {
        return find(versionId).orElseThrow(() -> new CoreException(ErrorType.AGENT_PROVENANCE_NOT_FOUND));
    }

    public Optional<AgentProvenance> find(AgentVersionId versionId) {
        return agentProvenanceRepository.findById(versionId.value())
                .map(AgentProvenanceEntityConverter::toDomain);
    }

    // 에이전트가 처음 공개된 버전의 기록이다. 원작자가 이 에이전트를 처음 내놓은 때가 여기 있다
    public AgentProvenance readFirst(AgentId agentId) {
        return agentProvenanceRepository.findFirstByAgentIdOrderByVersionNoAsc(agentId.value())
                .map(AgentProvenanceEntityConverter::toDomain)
                .orElseThrow(() -> new CoreException(ErrorType.AGENT_PROVENANCE_NOT_FOUND));
    }
}
