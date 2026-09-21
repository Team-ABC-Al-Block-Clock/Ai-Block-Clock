package com.aiblockclock.enums.workflow;

public enum InputSource {
    USER,
    PREV,
    PREV_AND_USER;

    public static InputSource defaultSource() {
        return PREV;
    }
}
