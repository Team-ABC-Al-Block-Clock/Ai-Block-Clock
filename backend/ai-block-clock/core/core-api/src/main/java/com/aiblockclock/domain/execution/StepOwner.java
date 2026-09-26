package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

// 엔진이 돌려준 단계의 주인과 그 단계가 속한 에이전트다. 실패 책임과 정산이 같은 규칙으로 읽는다.
// 둘 다 우리가 보낸 값을 엔진이 그대로 돌려준 것이다. 보낸 모양이 아니면 가리키지 않는다.
// 캔버스에 바로 놓인 노드는 에이전트가 비어 있다.
record StepOwner(UserId supplier, AgentId agent) {
    private static final StepOwner UNKNOWN = new StepOwner(null, null);

    static StepOwner of(RunStatus.Node step) {
        if (step == null) {
            return UNKNOWN;
        }

        return new StepOwner(supplierOf(step.ownerId()), agentOf(step.agentId()));
    }

    boolean isKnown() {
        return supplier != null;
    }

    private static UserId supplierOf(String ownerId) {
        if (ownerId == null) {
            return null;
        }

        try {
            return UserId.of(Long.parseLong(ownerId));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static AgentId agentOf(String agentId) {
        if (agentId == null) {
            return null;
        }

        try {
            return AgentId.of(agentId);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
