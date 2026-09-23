package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.storage.agent.AgentVersionNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentVersionNodeReader {
    private final AgentVersionNodeRepository agentVersionNodeRepository;

    public Map<NodeKey, VersionNode> read(AgentVersionId versionId) {
        return agentVersionNodeRepository.findByAgentVersionId(versionId.value()).stream()
                .map(AgentVersionNodeEntityConverter::toDomain)
                .collect(Collectors.toMap(VersionNode::key, Function.identity()));
    }
}
