package com.aiblockclock.domain.execution;

import java.util.stream.Stream;

public sealed interface PlannedNode permits PlannedStep, PlannedBranch, PlannedAgent {
    String id();

    Stream<PlannedPrompt> prompts();
}
