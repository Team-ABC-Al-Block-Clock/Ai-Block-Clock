package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

public record PlacedUnit(NodeKey key, ExecutionUnit unit) {
}
