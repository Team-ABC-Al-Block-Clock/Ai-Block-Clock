package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.NodeKey;

import java.util.List;

public record AgentUnit(List<ExecutionUnit> inside) implements ExecutionUnit {
    public AgentUnit {
        inside = List.copyOf(inside);
    }

    // 속의 단계도 모두 바깥 자리에 속한다. 에이전트는 속을 숨기고 자리 하나로 값을 매긴다.
    @Override
    public List<PlannedPrompt> flatten(NodeKey placedKey) {
        return inside.stream()
                .flatMap(unit -> unit.flatten(placedKey).stream())
                .toList();
    }
}
