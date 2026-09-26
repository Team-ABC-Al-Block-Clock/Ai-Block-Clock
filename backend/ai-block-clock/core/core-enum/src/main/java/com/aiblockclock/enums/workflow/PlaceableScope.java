package com.aiblockclock.enums.workflow;

public enum PlaceableScope {
    MINE,
    ALL;

    public boolean includesOthers() {
        return this == ALL;
    }
}
