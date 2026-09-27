package com.aiblockclock.domain.execution;

public final class ExecutionPlanner {
    private ExecutionPlanner() {
    }

    public static ExecutionPlan plan(UnitGraph canvas) {
        PlanLevel level = PlanLevel.canvas().lay(canvas);

        return new ExecutionPlan(level.nodes(), level.edges());
    }
}
