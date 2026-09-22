package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentVersionId;
import com.aiblockclock.domain.agent.AgentVersionReader;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.workflow.GraphNode;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExecutionUnitAssembler {
    private final NodeDefinitionReader nodeReader;
    private final AgentVersionReader agentVersionReader;

    public ExecutionUnit assemble(GraphNode placed) {
        return switch (placed.sourceType()) {
            case NODE -> new NodeUnit(
                    placed.inputSource(),
                    nodeReader.read(NodeDefinitionId.of(placed.reference().referenceId())));
            case AGENT -> expand(placed);
        };
    }

    private ExecutionUnit expand(GraphNode placed) {
        AgentVersionId versionId = AgentVersionId.of(placed.reference().versionId());

        return new AgentUnit(agentVersionReader.readGraph(versionId).nodesInOrder().stream()
                .map(this::assemble)
                .toList());
    }
}
