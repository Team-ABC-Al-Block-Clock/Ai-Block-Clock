package com.aiblockclock.domain.workflow;

public record EdgeKey(String value) {
    public static EdgeKey of(String value) {
        return new EdgeKey(value);
    }

}
