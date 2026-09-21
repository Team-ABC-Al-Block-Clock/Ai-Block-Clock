package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

public record NodeUnit(InputSource inputSource, NodeDefinition node) implements ExecutionUnit {
    @Override
    public List<PlannedPrompt> flatten() {
        return List.of(new PlannedPrompt(node.getName(), node.getPrompt(), inputSource));
    }
}
