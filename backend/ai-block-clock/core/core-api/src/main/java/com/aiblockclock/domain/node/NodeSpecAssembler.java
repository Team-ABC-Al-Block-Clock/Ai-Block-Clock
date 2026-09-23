package com.aiblockclock.domain.node;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeSpecAssembler {
    private final BaseCostCalculator baseCostCalculator;

    public NodeSpec assemble(String name, Prompt prompt, AiModel model) {
        return new NodeSpec(name, NodeType.LLM, model, baseCostCalculator.calculate(prompt, model));
    }
}
