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

@Component
@RequiredArgsConstructor
public class VersionCostAssembler {
    private final NodeDefinitionReader nodeReader;

    public VersionCost assemble(WorkflowGraph snapshot) {
        List<NodeDefinitionId> nodeIds = snapshot.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(NodeDefinitionId::of)
                .toList();

        BigDecimal total = nodeReader.readAll(nodeIds).stream()
                .map(node -> node.getSpec().baseCost().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new VersionCost(nodeIds.size(), AbcAmount.of(total));
    }
}
