package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

public interface ExecutionUnit {
    void place(NodeKey key, PlanLevel level);
}
