package com.aiblockclock.enums.node;

public enum NodeType {
    TRIGGER,
    LLM,
    TOOL;

    public boolean isAuthoredByUser() {
        return this == LLM;
    }
}
