package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

public record ControlNode(NodeKey key, ControlType controlType, BranchCondition condition) implements GraphNode {
    @Override
    public SourceType sourceType() {
        return SourceType.CONTROL;
    }

    @Override
    public NodeReference reference() {
        return NodeReference.of(null);
    }

    @Override
    public InputSource inputSource() {
        return InputSource.defaultSource();
    }
}
