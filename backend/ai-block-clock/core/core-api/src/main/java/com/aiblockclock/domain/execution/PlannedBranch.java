package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.workflow.ConditionOperator;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PlannedBranch(String id, String left, ConditionOperator operator, Object right) implements PlannedNode {
    @Override
    public Stream<PlannedPrompt> prompts() {
        return Stream.empty();
    }

    @Override
    public Map<String, Object> toRuntime(Map<String, List<RefFile>> refFiles) {
        return RuntimeWorkflow.branch(this);
    }
}
