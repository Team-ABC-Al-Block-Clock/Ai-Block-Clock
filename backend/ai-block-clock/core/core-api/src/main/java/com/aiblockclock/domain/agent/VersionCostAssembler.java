package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class VersionCostAssembler {
    private final NodeDefinitionReader nodeReader;

    public VersionCost assemble(WorkflowGraph snapshot) {
        List<NodeDefinitionId> placed = snapshot.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(NodeDefinitionId::of)
                .toList();

        Map<NodeDefinitionId, BigDecimal> costs = nodeReader.readAll(placed.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(
                        NodeDefinition::getId, node -> node.getSpec().baseCost().value()));

        BigDecimal total = placed.stream()
                .map(costs::get)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VersionCost(placed.size(), AbcAmount.of(total));
    }
}
