package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// 닫힌 실행 하나가 에이전트 등급(PAY-017 · 018)에 남기는 것이다. 에이전트마다 성공 한 번이나 공급자 탓 실패 한 번이다.
// 에이전트 안의 단계들을 한 덩어리로 본다. 에이전트는 속을 숨기고 한 번 팔리므로, 단계가 여럿이어도 한 번이다.
// 세지 않는 것:
//   - 플랫폼 탓이거나 끝을 모르는 실행. 에이전트가 무엇을 했는지와 상관없이 우리가 막았다
//   - 자기 에이전트를 자기가 쓴 실행. 원가만 내고 사용량과 성공률을 스스로 올릴 수 있다 (301 · 302 의 selfUse 와 같다)
//   - 모델을 부르지 않은 성공(mock). 실측 원가가 하나도 없다
//   - 끝까지 돌지 못했지만 그 에이전트 탓은 아닌 경우. 수요자 탓, 성공률에 넣지 않는 공급자 오류, 앞 단계에서 멈춤
// 캔버스에 바로 놓인 노드는 에이전트가 아니라서 세지 않는다.
public record AgentRunOutcomes(Set<AgentId> succeeded, Set<AgentId> failed) {
    private static final AgentRunOutcomes NONE = new AgentRunOutcomes(Set.of(), Set.of());

    public AgentRunOutcomes {
        succeeded = Set.copyOf(succeeded);
        failed = Set.copyOf(failed);
    }

    // failure 는 실패한 실행의 책임 판정이다. 성공한 실행은 비어 있다.
    // run 은 엔진이 알린 마지막 상태다. 기한 안에 끝을 알리지 않아 우리가 끊은 실행은 비어 있다.
    public static AgentRunOutcomes of(RunStatus run, ExecutionFailure failure, UserId requester) {
        if (run == null || run.nodes() == null || failure != null && failure.isPlatformFault()) {
            return NONE;
        }

        Set<AgentId> succeeded = new HashSet<>();
        Set<AgentId> failed = new HashSet<>();

        for (Map.Entry<AgentId, List<RunStatus.Node>> placed : stepsByAgent(run.nodes()).entrySet()) {
            AgentId agent = placed.getKey();
            List<RunStatus.Node> steps = placed.getValue();

            if (isSelfUse(steps, requester)) {
                continue;
            }

            if (failure != null && failure.countsAgainst(agent)) {
                failed.add(agent);
            } else if (isCompleted(steps)) {
                succeeded.add(agent);
            }
        }

        return new AgentRunOutcomes(succeeded, failed);
    }

    private static Map<AgentId, List<RunStatus.Node>> stepsByAgent(List<RunStatus.Node> nodes) {
        return nodes.stream()
                .filter(node -> StepOwner.of(node).agent() != null)
                .collect(Collectors.groupingBy(node -> StepOwner.of(node).agent(), LinkedHashMap::new,
                        Collectors.toList()));
    }

    // 에이전트 안의 단계는 모두 그 에이전트 주인의 것이다(V6__create_agent.sql). 하나라도 실행한 사람의 것이면 자기 실행이다.
    private static boolean isSelfUse(List<RunStatus.Node> steps, UserId requester) {
        return steps.stream()
                .map(StepOwner::of)
                .anyMatch(owner -> requester.equals(owner.supplier()));
    }

    // 모든 단계가 성공했거나 분기에서 고르지 않아 건너뛰었고, 모델을 실제로 부른 단계가 하나는 있다.
    private static boolean isCompleted(List<RunStatus.Node> steps) {
        boolean finished = steps.stream()
                .allMatch(step -> step.isSucceeded() || step.isSkipped());
        boolean measured = steps.stream()
                .anyMatch(step -> step.isSucceeded() && step.costUsd() != null && step.costUsd().signum() > 0);

        return finished && measured;
    }
}
