package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentReader {
    private final AgentRepository agentRepository;

    public boolean isOwnedBy(AgentId id, UserId owner) {
        return agentRepository.existsByIdAndOwnerId(id.value(), owner.value());
    }
}
