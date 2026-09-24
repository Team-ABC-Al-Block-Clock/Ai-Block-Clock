package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.Agent;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentReader;
import com.aiblockclock.domain.agent.AgentVersion;
import com.aiblockclock.domain.agent.AgentVersionId;
import com.aiblockclock.domain.agent.AgentVersionReader;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class CostEstimateAssembler {
    private final NodeDefinitionReader nodeReader;
    private final AgentReader agentReader;
    private final AgentVersionReader agentVersionReader;

    // 캔버스의 노드는 지금의 원가를, 에이전트는 놓을 때 박힌 버전의 판매가를 읽는다. 내 에이전트는 원가만.
    // 자기 워크플로우만 견적 · 실행할 수 있으므로 워크플로우 주인이 곧 내는 사람이다.
    public CostEstimate assemble(Workflow workflow) {
        UserId payer = workflow.getOwner();

        return CostEstimate.of(workflow.getGraph().nodesInOrder().stream()
                .map(placed -> itemOf(placed, payer))
                .toList());
    }

    private CostItem itemOf(GraphNode placed, UserId payer) {
        return switch (placed.sourceType()) {
            case NODE -> {
                NodeDefinition node = nodeReader.read(NodeDefinitionId.of(placed.reference().referenceId()));

                yield new CostItem(placed.key(), SourceType.NODE, node.getSpec().name(), node.getSpec().baseCost(),
                        BigDecimal.ONE);
            }
            case AGENT -> {
                Agent agent = agentReader.read(AgentId.of(placed.reference().referenceId()));
                AgentVersion version = agentVersionReader.read(AgentVersionId.of(placed.reference().versionId()));

                yield new CostItem(placed.key(), SourceType.AGENT, agent.getProfile().name(),
                        priceFor(payer, agent, version), multiplierFor(payer, agent, version));
            }
            // 분기는 아직 실행하지 못한다. 실행 조립(ExecutionUnitAssembler)과 같은 규칙으로 견적도 내지 않는다.
            case CONTROL -> throw new CoreException(ErrorType.EXECUTION_BRANCH_NOT_SUPPORTED);
        };
    }

    // 할증은 판매자 몫이라, 자기 에이전트를 자기가 쓰면 원가만 낸다.
    private static AbcAmount priceFor(UserId payer, Agent agent, AgentVersion version) {
        return agent.isOwnedBy(payer) ? version.getCost().baseCost() : version.finalPrice();
    }

    private static BigDecimal multiplierFor(UserId payer, Agent agent, AgentVersion version) {
        return agent.isOwnedBy(payer) ? BigDecimal.ONE : version.getTier().priceMultiplier();
    }
}
