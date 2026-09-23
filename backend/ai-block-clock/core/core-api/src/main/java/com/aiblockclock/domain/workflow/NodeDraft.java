package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.node.AiModel;

public record NodeDraft(String name, String prompt, AiModel model) {
}
