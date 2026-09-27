package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.ConditionOperator;

import java.util.stream.Stream;

public record PlannedBranch(String id, String left, ConditionOperator operator, Object right) implements PlannedNode {
    @Override
    public Stream<PlannedPrompt> prompts() {
        return Stream.empty();
    }
}
