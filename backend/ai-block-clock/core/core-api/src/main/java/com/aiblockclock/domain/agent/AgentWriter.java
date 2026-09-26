package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AgentWriter {
    private final AgentRepository agentRepository;

    @Transactional
    public void append(Agent agent) {
        agentRepository.save(AgentEntityConverter.toEntity(agent));
    }
}
