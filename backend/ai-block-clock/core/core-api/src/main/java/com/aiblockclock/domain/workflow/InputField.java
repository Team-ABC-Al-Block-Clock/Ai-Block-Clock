package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.InputFieldType;

import java.util.List;

public record InputField(String key, String label, InputFieldType type, boolean required, List<String> choices) {
    public InputField {
        choices = choices == null ? List.of() : List.copyOf(choices);
    }
}
