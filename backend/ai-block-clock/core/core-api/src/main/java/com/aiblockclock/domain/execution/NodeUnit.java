package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;

import java.util.List;

public record NodeUnit(
        InputSource inputSource, UserId owner, String name, Prompt prompt, AiModel model, NodeOutput output,
        List<RefFile> refFiles, AbcAmount baseCost)
        implements ExecutionUnit {
    public NodeUnit {
        refFiles = List.copyOf(refFiles);
    }

    @Override
    public void place(NodeKey key, PlanLevel level) {
        level.step(key, this);
    }

    PlannedPrompt plan(String stepId, NodeKey placedKey) {
        return new PlannedPrompt(stepId, placedKey, name, owner, prompt, model, output, inputSource,
                refFiles, baseCost);
    }
}
