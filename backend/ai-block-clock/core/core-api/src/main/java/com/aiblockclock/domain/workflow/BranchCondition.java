package com.aiblockclock.domain.workflow;

public record BranchCondition(String left, ConditionOperator operator, Object right) {
}
