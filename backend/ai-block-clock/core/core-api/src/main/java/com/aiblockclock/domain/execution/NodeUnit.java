package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

public record NodeUnit(InputSource inputSource, String name, Prompt prompt) implements ExecutionUnit {
    @Override
    public List<PlannedPrompt> flatten() {
        return List.of(new PlannedPrompt(name, prompt, inputSource));
    }
}
