package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class VersionCostAssembler {
    private VersionCostAssembler() {
    }

    public static VersionCost assemble(WorkflowGraph snapshot, List<NodeDefinition> placedNodes) {
        Map<NodeDefinitionId, BigDecimal> costs = placedNodes.stream()
                .collect(Collectors.toMap(
                        NodeDefinition::getId, node -> node.getSpec().baseCost().value()));

        List<NodeDefinitionId> placed = snapshot.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(NodeDefinitionId::of)
                .toList();

        BigDecimal total = placed.stream()
                .map(costs::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VersionCost(placed.size(), AbcAmount.of(total));
    }
}
