package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.RefFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public record PlannedStep(String id, PlannedPrompt prompt) implements PlannedNode {
    @Override
    public Stream<PlannedPrompt> prompts() {
        return Stream.of(prompt);
    }

    @Override
    public Map<String, Object> toRuntime(Map<String, List<RefFile>> refFiles) {
        return RuntimeWorkflow.step(id, prompt, refFiles);
    }
}
