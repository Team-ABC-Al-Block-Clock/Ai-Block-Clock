package com.aiblockclock.domain.node;

import com.aiblockclock.enums.node.OutputKind;

public record NodeOutput(OutputKind kind, String example) {
}
