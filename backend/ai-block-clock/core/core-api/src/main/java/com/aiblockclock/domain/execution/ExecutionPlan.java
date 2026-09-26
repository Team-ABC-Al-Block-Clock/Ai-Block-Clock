package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.node.AiModel;

import java.util.List;

public record ExecutionPlan(List<PlannedPrompt> prompts) {
    public ExecutionPlan {
        prompts = List.copyOf(prompts);
    }

    public int promptCount() {
        return prompts.size();
    }

    public AiModel model() {
        return prompts.get(0).model();
    }
}
