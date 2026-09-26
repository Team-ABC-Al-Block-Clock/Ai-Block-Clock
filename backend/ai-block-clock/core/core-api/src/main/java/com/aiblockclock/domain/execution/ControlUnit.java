package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.util.List;

public record ControlUnit() implements ExecutionUnit {
    @Override
    public List<PlannedPrompt> flatten(NodeKey placedKey) {
        return List.of();
    }
}
