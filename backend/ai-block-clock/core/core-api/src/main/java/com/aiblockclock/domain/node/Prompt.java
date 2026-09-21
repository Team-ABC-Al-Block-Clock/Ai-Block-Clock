package com.aiblockclock.domain.node;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

public record Prompt(String value) {
    public Prompt {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.NODE_PROMPT_EMPTY);
        }
    }

    public static Prompt of(String value) {
        return new Prompt(value);
    }
}
