package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.workflow.NodeKey;

// 속의 단계도 모두 바깥 자리에 속한다. 에이전트는 속을 숨기고 자리 하나로 값을 매긴다.
public record AgentUnit(AgentId agent, UnitGraph inside) implements ExecutionUnit {
    @Override
    public void place(NodeKey key, PlanLevel level) {
        level.agent(key, this);
    }
}
