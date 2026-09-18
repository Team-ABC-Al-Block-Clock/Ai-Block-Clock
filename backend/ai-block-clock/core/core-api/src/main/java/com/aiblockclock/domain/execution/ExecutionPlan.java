package com.aiblockclock.domain.execution;

import java.util.List;

public record ExecutionPlan(List<PlannedPrompt> prompts) {
    public ExecutionPlan {
        prompts = List.copyOf(prompts);
    }

    public int promptCount() {
        return prompts.size();
    }
}
