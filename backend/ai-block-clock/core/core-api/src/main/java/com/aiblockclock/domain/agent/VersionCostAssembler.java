package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class VersionCostAssembler {
    private VersionCostAssembler() {
    }

    // 놓인 버전은 제 값을 이미 들고 있으므로 한 단계만 더한다. 몇 겹이든 재귀가 필요 없다.
    public static VersionCost assemble(
            WorkflowGraph snapshot,
            Map<NodeDefinitionId, NodeDefinition> placedNodes,
            Map<AgentVersionId, VersionCost> placedVersions) {
        List<VersionCost> costs = Stream.concat(
                        snapshot.baseNodeReferences().stream()
                                .map(reference -> nodeCost(reference, placedNodes)),
                        snapshot.agentReferences().stream()
                                .map(reference -> placedVersions.get(AgentVersionId.of(reference.versionId()))))
                .toList();

        int promptNodeCount = costs.stream().mapToInt(VersionCost::promptNodeCount).sum();
        BigDecimal baseCost = costs.stream()
                .map(cost -> cost.baseCost().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VersionCost(promptNodeCount, AbcAmount.of(baseCost));
    }

    private static VersionCost nodeCost(NodeReference reference, Map<NodeDefinitionId, NodeDefinition> placedNodes) {
        NodeDefinition node = placedNodes.get(NodeDefinitionId.of(reference.referenceId()));

        return new VersionCost(1, node.getSpec().baseCost());
    }
}
