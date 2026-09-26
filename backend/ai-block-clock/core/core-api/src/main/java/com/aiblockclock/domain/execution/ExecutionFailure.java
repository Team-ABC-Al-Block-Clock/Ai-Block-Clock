package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionFault;

import java.util.List;
import java.util.Locale;

// 실패한 실행이 누구 탓인지다. 판정은 엔진이 하고(계약 8.5 · 9.2), 여기서는 받은 것을 한 가지 규칙으로 정리한다.
// 책임을 가릴 근거가 없으면 플랫폼 탓으로 본다. 공급자나 수요자에게 넘길 근거가 없는 실패는 우리가 진다.
// supplier · agent 는 공급자 탓일 때만 채운다. 실패한 단계의 주인과 그 단계가 속한 에이전트다.
public record ExecutionFailure(
        String stepId,
        String errorCode,
        ExecutionFault fault,
        boolean affectsSuccessRate,
        UserId supplier,
        AgentId agent
) {
    public static final String UNREACHABLE = "ABC_RUNTIME_UNREACHABLE";

    // run 은 엔진이 알린 마지막 상태다. 기한 안에 끝을 알리지 않아 우리가 끊은 실행은 비어 있다.
    public static ExecutionFailure judge(RunStatus run) {
        if (run == null) {
            return platform(ExecutionProgress.STALLED);
        }

        RunStatus.Failure reported = run.failure();

        if (reported == null) {
            return platform(ExecutionProgress.UNREPORTED);
        }

        ExecutionFault fault = faultOf(reported.fault());

        if (fault != ExecutionFault.SUPPLIER) {
            return new ExecutionFailure(reported.nodeId(), reported.errorCode(), fault, false, null, null);
        }

        RunStatus.Node failed = stepOf(run.nodes(), reported.nodeId());

        return new ExecutionFailure(reported.nodeId(), reported.errorCode(), fault,
                Boolean.TRUE.equals(reported.affectsSuccessRate()), supplierOf(failed), agentOf(failed));
    }

    // 엔진이 받지 않은 실행이다. 어느 단계도 돌지 않았다.
    public static ExecutionFailure undispatched() {
        return platform(UNREACHABLE);
    }

    public boolean isPlatformFault() {
        return fault == ExecutionFault.PLATFORM;
    }

    private static ExecutionFailure platform(String errorCode) {
        return new ExecutionFailure(null, errorCode, ExecutionFault.PLATFORM, false, null, null);
    }

    private static ExecutionFault faultOf(String reported) {
        if (reported == null) {
            return ExecutionFault.PLATFORM;
        }

        try {
            return ExecutionFault.valueOf(reported.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return ExecutionFault.PLATFORM;
        }
    }

    private static RunStatus.Node stepOf(List<RunStatus.Node> nodes, String stepId) {
        if (nodes == null || stepId == null) {
            return null;
        }

        return nodes.stream()
                .filter(node -> stepId.equals(node.id()))
                .findFirst()
                .orElse(null);
    }

    // 주인과 에이전트는 우리가 보낸 값을 엔진이 그대로 돌려준 것이다. 보낸 모양이 아니면 가리키지 않는다.
    private static UserId supplierOf(RunStatus.Node failed) {
        if (failed == null || failed.ownerId() == null) {
            return null;
        }

        try {
            return UserId.of(Long.parseLong(failed.ownerId()));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private static AgentId agentOf(RunStatus.Node failed) {
        if (failed == null || failed.agentId() == null) {
            return null;
        }

        try {
            return AgentId.of(failed.agentId());
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }
}
