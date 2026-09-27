package com.aiblockclock.domain.execution;

import java.util.stream.Stream;

public record PlannedStep(String id, PlannedPrompt prompt) implements PlannedNode {
    @Override
    public Stream<PlannedPrompt> prompts() {
        return Stream.of(prompt);
    }
}
