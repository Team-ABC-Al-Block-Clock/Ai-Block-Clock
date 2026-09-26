package com.aiblockclock.domain.workflow;

public record Revision(long value) {
    public static Revision initial() {
        return new Revision(1L);
    }

    public static Revision of(long value) {
        return new Revision(value);
    }

    public Revision next() {
        return new Revision(value + 1);
    }

}
