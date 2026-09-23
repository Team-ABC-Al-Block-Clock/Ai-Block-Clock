package com.aiblockclock.domain.workflow;

import java.util.Optional;

public record BranchCondition(String left, ConditionOperator operator, Object right) {
    public Optional<NodeKey> source() {
        int dot = left.indexOf('.');

        if (dot <= 0 || dot == left.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(NodeKey.of(left.substring(0, dot)));
    }
}
