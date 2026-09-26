package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;

public record NodeDraft(String name, String prompt, AiModel model, OutputKind outputKind, String outputSample) {
}
