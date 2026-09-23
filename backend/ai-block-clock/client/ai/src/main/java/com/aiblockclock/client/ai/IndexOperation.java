package com.aiblockclock.client.ai;

public enum IndexOperation {
    UPSERT("upsert"),
    REBUILD("rebuild");

    private final String wireName;

    IndexOperation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
