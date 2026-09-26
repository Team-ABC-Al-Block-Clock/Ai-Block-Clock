package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.agent.AgentFavoriteRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Transactional
public class AgentFavoriteWriter {
    private final AgentFavoriteRepository agentFavoriteRepository;

    public void append(UserId user, AgentId agent) {
        agentFavoriteRepository.insertIfAbsent(user.value(), agent.value().toString(), Instant.now());
    }

    public void remove(UserId user, AgentId agent) {
        agentFavoriteRepository.deleteByUserIdAndAgentId(user.value(), agent.value());
    }
}
