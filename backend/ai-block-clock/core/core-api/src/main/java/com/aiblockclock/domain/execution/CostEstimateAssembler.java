package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.Agent;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentReader;
import com.aiblockclock.domain.agent.AgentVersion;
import com.aiblockclock.domain.agent.AgentVersionId;
import com.aiblockclock.domain.agent.AgentVersionReader;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.SourceType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CostEstimateAssembler {
    private final NodeDefinitionReader nodeReader;
    private final AgentReader agentReader;
    private final AgentVersionReader agentVersionReader;

    // 캔버스의 노드는 지금의 원가를, 에이전트는 놓을 때 박힌 버전의 판매가를 읽는다.
    public CostEstimate assemble(Workflow workflow) {
        return CostEstimate.of(workflow.getGraph().nodesInOrder().stream()
                .map(this::itemOf)
                .toList());
    }

    private CostItem itemOf(GraphNode placed) {
        return switch (placed.sourceType()) {
            case NODE -> {
                NodeDefinition node = nodeReader.read(NodeDefinitionId.of(placed.reference().referenceId()));

                yield new CostItem(placed.key(), SourceType.NODE, node.getSpec().name(), node.getSpec().baseCost());
            }
            case AGENT -> {
                Agent agent = agentReader.read(AgentId.of(placed.reference().referenceId()));
                AgentVersion version = agentVersionReader.read(AgentVersionId.of(placed.reference().versionId()));

                yield new CostItem(placed.key(), SourceType.AGENT, agent.getProfile().name(), version.finalPrice());
            }
        };
    }
}
