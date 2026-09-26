package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.util.List;

public interface ExecutionUnit {
    List<PlannedPrompt> flatten(NodeKey placedKey);
}
