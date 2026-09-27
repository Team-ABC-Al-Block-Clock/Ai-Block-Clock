package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.NodeKey;

public record ControlUnit(BranchCondition condition) implements ExecutionUnit {
    @Override
    public void place(NodeKey key, PlanLevel level) {
        level.branch(key, condition);
    }
}
