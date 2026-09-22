package com.aiblockclock.client.ai;

public enum IndexOperation {
    UPSERT("upsert"),
    REMOVE("remove"),
    REBUILD("rebuild");

    private final String wireName;

    IndexOperation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
