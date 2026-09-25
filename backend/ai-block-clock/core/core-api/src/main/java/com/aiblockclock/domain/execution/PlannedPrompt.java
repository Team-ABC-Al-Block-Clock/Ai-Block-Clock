package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

public record PlannedPrompt(
        String name, UserId owner, Prompt prompt, InputSource inputSource, List<RefFile> refFiles) {
    public PlannedPrompt {
        refFiles = List.copyOf(refFiles);
    }
}
