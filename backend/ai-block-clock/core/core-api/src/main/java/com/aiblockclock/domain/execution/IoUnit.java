package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

public record IoUnit() implements ExecutionUnit {
    @Override
    public void place(NodeKey key, PlanLevel level) {
    }
}
