package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentVersionNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentVersionNodeWriter {
    private final AgentVersionNodeRepository agentVersionNodeRepository;

    @Transactional
    public void append(AgentVersionId versionId, List<VersionNode> nodes) {
        agentVersionNodeRepository.saveAll(nodes.stream()
                .map(node -> AgentVersionNodeEntityConverter.toEntity(versionId, node))
                .toList());
    }
}
