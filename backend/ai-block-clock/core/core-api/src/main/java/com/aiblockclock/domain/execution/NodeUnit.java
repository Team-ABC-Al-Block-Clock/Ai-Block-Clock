package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

public record NodeUnit(
        InputSource inputSource, UserId owner, String name, Prompt prompt, AiModel model,
        List<RefFile> refFiles, AbcAmount baseCost)
        implements ExecutionUnit {
    public NodeUnit {
        refFiles = List.copyOf(refFiles);
    }

    @Override
    public List<PlannedPrompt> flatten(NodeKey placedKey) {
        return List.of(new PlannedPrompt(placedKey, name, owner, prompt, model, inputSource, refFiles, baseCost));
    }
}
