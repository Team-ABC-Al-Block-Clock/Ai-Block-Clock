package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class VersionCostAssembler {
    private VersionCostAssembler() {
    }

    public static VersionCost assemble(
            WorkflowGraph snapshot, Map<NodeDefinitionId, NodeDefinition> placedNodes) {
        List<BigDecimal> costs = snapshot.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(NodeDefinitionId::of)
                .map(placedNodes::get)
                .map(node -> node.getSpec().baseCost().value())
                .toList();

        return new VersionCost(costs.size(),
                AbcAmount.of(costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add)));
    }
}
