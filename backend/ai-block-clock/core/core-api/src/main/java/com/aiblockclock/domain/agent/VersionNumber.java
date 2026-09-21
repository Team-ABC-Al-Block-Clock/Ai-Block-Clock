package com.aiblockclock.domain.agent;

public record VersionNumber(int value) {
    private static final int FIRST = 1;

    public static VersionNumber first() {
        return new VersionNumber(FIRST);
    }

    public static VersionNumber of(int value) {
        return new VersionNumber(value);
    }

    public VersionNumber next() {
        return new VersionNumber(value + 1);
    }

    public boolean isAfter(VersionNumber other) {
        return value > other.value;
    }

}
