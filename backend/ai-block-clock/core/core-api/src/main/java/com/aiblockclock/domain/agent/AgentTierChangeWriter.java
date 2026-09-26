package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentTierChangeEntity;
import com.aiblockclock.storage.agent.AgentTierChangeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AgentTierChangeWriter {
    private final AgentTierChangeRepository agentTierChangeRepository;

    // 바뀐 버전마다 한 줄이다. gradeHistoryId 는 이 등급을 매긴 근거인 등급 이력의 줄이다.
    public void append(long gradeHistoryId, List<AgentTierChange> changes) {
        agentTierChangeRepository.saveAll(changes.stream()
                .map(change -> toEntity(gradeHistoryId, change))
                .toList());
    }

    private static AgentTierChangeEntity toEntity(long gradeHistoryId, AgentTierChange change) {
        return new AgentTierChangeEntity(
                change.agent().value(),
                change.version().value(),
                gradeHistoryId,
                change.before(),
                change.after(),
                change.beforePrice().value(),
                change.afterPrice().value());
    }
}
