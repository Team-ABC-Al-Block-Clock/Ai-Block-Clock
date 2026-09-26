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

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.stream.Stream;

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
                .flatMap(placed -> itemOf(placed, payer))
                .toList());
    }

    private Stream<CostItem> itemOf(GraphNode placed, UserId payer) {
        return switch (placed.sourceType()) {
            case NODE -> {
                NodeDefinition node = nodeReader.read(NodeDefinitionId.of(placed.reference().referenceId()));

                yield Stream.of(new CostItem(placed.key(), SourceType.NODE, node.getSpec().name(),
                        node.getSpec().baseCost(), BigDecimal.ONE));
            }
            case AGENT -> {
                Agent agent = agentReader.read(AgentId.of(placed.reference().referenceId()));
                AgentVersion version = agentVersionReader.read(AgentVersionId.of(placed.reference().versionId()));

                yield Stream.of(new CostItem(placed.key(), SourceType.AGENT, agent.getProfile().name(),
                        priceFor(payer, agent, version), multiplierFor(payer, agent, version)));
            }
            // 분기 노드는 값이 없다. 어느 갈래로 갈지 모르므로 두 갈래를 다 더해 떼고, 안 간 쪽은 끝난 뒤 돌려준다.
            case CONTROL -> Stream.empty();
            case IO -> Stream.empty();
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
