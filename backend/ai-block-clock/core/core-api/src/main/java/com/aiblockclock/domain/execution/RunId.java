package com.aiblockclock.domain.execution;

public record RunId(String value) {
    public static RunId of(String value) {
        return new RunId(value);
    }
}
