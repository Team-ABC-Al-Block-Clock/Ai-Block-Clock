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

    // 우리가 끊은 실행(기한 초과 · 엔진이 잊음 · dead letter)은 엔진이 마지막으로 무엇을 알렸든 플랫폼 탓이다.
    // 끊기 전에 멈춰 있던 단계를 실패 단계로 남겨, 어디서 멎었는지를 근거로 보일 수 있게 한다.
    public static ExecutionFailure judge(EngineReport report) {
        return switch (report.closedBy()) {
            case ENGINE -> judge(report.run());
            case STALLED -> cut(report, ExecutionProgress.STALLED);
            case LOST -> cut(report, ExecutionProgress.LOST);
            case UNDISPATCHED -> undispatched();
            case DEAD_LETTERED -> cut(report, report.failureCode());
        };
    }

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

        StepOwner owner = StepOwner.of(stepOf(run.nodes(), reported.nodeId()));

        return new ExecutionFailure(reported.nodeId(), reported.errorCode(), fault,
                Boolean.TRUE.equals(reported.affectsSuccessRate()), owner.supplier(), owner.agent());
    }

    // 엔진이 받지 않은 실행이다. 어느 단계도 돌지 않았다.
    public static ExecutionFailure undispatched() {
        return platform(UNREACHABLE);
    }

    public boolean isPlatformFault() {
        return fault == ExecutionFault.PLATFORM;
    }

    // 이 실패가 그 에이전트의 성공률을 깎는가다. 공급자 탓이어도 엔진이 성공률에 넣지 말라고 한 오류는 깎지 않는다.
    public boolean countsAgainst(AgentId candidate) {
        return fault == ExecutionFault.SUPPLIER && affectsSuccessRate && candidate.equals(agent);
    }

    private static ExecutionFailure platform(String errorCode) {
        return new ExecutionFailure(null, errorCode, ExecutionFault.PLATFORM, false, null, null);
    }

    private static ExecutionFailure cut(EngineReport report, String errorCode) {
        return new ExecutionFailure(report.currentStepId(), errorCode, ExecutionFault.PLATFORM, false, null, null);
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
}
