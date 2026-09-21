package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.enums.workflow.InputSource;

public record PlannedPrompt(String name, Prompt prompt, InputSource inputSource) {
}
