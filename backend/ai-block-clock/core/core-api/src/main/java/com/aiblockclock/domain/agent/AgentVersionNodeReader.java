package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.storage.agent.AgentVersionNodeRefFileEntity;
import com.aiblockclock.storage.agent.AgentVersionNodeRefFileRepository;
import com.aiblockclock.storage.agent.AgentVersionNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentVersionNodeReader {
    private final AgentVersionNodeRepository agentVersionNodeRepository;
    private final AgentVersionNodeRefFileRepository agentVersionNodeRefFileRepository;

    public Map<NodeKey, VersionNode> read(AgentVersionId versionId) {
        Map<String, List<RefFile>> refFiles = readRefFiles(versionId);

        return agentVersionNodeRepository.findByAgentVersionId(versionId.value()).stream()
                .map(entity -> AgentVersionNodeEntityConverter.toDomain(
                        entity, refFiles.getOrDefault(entity.getNodeKey(), List.of())))
                .collect(Collectors.toMap(VersionNode::key, Function.identity()));
    }

    private Map<String, List<RefFile>> readRefFiles(AgentVersionId versionId) {
        return agentVersionNodeRefFileRepository
                .findByVersionIdOrderByNodeKeyAscOrdinalAsc(versionId.value()).stream()
                .collect(Collectors.groupingBy(
                        AgentVersionNodeRefFileEntity::getNodeKey,
                        Collectors.mapping(AgentVersionNodeEntityConverter::toRefFile, Collectors.toList())));
    }
}
