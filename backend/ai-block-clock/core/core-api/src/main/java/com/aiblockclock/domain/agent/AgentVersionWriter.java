package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.storage.agent.AgentVersionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AgentVersionWriter {
    private final AgentVersionRepository agentVersionRepository;

    @Transactional
    public void append(AgentVersion version, GraphId graphId) {
        agentVersionRepository.save(AgentVersionEntityConverter.toEntity(version, graphId));
    }
}
