package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.util.List;

public record IoUnit() implements ExecutionUnit {
    @Override
    public List<PlannedPrompt> flatten(NodeKey placedKey) {
        return List.of();
    }
}
