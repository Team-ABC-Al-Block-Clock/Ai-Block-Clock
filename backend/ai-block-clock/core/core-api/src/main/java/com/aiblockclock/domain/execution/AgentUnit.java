package com.aiblockclock.domain.execution;

import java.util.List;

public record AgentUnit(List<ExecutionUnit> inside) implements ExecutionUnit {
    public AgentUnit {
        inside = List.copyOf(inside);
    }

    @Override
    public List<PlannedPrompt> flatten() {
        return inside.stream()
                .flatMap(unit -> unit.flatten().stream())
                .toList();
    }
}
