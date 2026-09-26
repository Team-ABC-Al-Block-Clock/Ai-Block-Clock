package com.aiblockclock.enums.workflow;

public enum InputFieldType {
    TEXT,
    LONG_TEXT,
    NUMBER,
    CHOICE,
    BOOLEAN;

    public boolean needsChoices() {
        return this == CHOICE;
    }
}
