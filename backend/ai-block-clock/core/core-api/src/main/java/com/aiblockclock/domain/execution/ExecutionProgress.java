package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunState;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.enums.execution.ExecutionStatus;

import java.util.List;
import java.util.Locale;

public record ExecutionProgress(
        ExecutionId id,
        ExecutionStatus status,
        int done,
        int total,
        int percent,
        String currentStepName,
        List<Step> steps,
        Failure failure
) {
    public static final String STALLED = "ABC_RUNTIME_STALLED";
    public static final String UNREPORTED = "ABC_RUNTIME_UNREPORTED";
    public static final String LOST = "ABC_RUNTIME_LOST";
    // 브로커가 실행 맡기기를 dead letter 로 보냈다 (ai/docs/integration-guide.md §4.8). 셋 다 플랫폼 탓이다.
    //   QUEUE_EXPIRED    런타임이 받기 전에 큐에서 10분을 넘겨 기다렸다
    //   RETRY_EXHAUSTED  두 번 배달했는데도 런타임이 끝내지 못했다
    //   DEAD_LETTERED    그 밖의 까닭으로 죽었다
    public static final String QUEUE_EXPIRED = "ABC_QUEUE_EXPIRED";
    public static final String RETRY_EXHAUSTED = "ABC_RUNTIME_RETRY_EXHAUSTED";
    public static final String DEAD_LETTERED = "ABC_RUNTIME_DEAD_LETTERED";

    public static ExecutionProgress of(ExecutionId id, RunStatus run, int promptCount) {
        return of(id, toStatus(run.state()), run, promptCount);
    }

    // 이벤트로 받은 snapshot 이다. 상태는 snapshot.status 가 아니라 이벤트 type 으로 정해 넘긴다.
    public static ExecutionProgress of(ExecutionId id, ExecutionStatus status, RunStatus run, int promptCount) {
        RunStatus.Progress progress = run.progress();
        List<RunStatus.Node> nodes = run.nodes() == null ? List.of() : run.nodes();
        String currentStepName = progress == null ? null : progress.currentNodeName();
        int total = progress == null || progress.total() <= 0 ? promptCount : progress.total();

        return new ExecutionProgress(
                id,
                status,
                progress == null ? 0 : progress.done(),
                total,
                progress == null ? 0 : progress.percent(),
                currentStepName,
                nodes.stream().map(Step::of).toList(),
                reasonFor(status, Failure.of(run.failure(), nodes), currentStepName));
    }

    // 우리가 남긴 상태로 만든 진행 상황이다. 엔진에 묻지 않아 단계 목록이 비어 있다.
    public static ExecutionProgress recorded(ExecutionId id, ExecutionStatus status, int stepCount) {
        return new ExecutionProgress(id, status, 0, stepCount, 0, null, List.of(),
                reasonFor(status, null, null));
    }

    public static ExecutionProgress concluded(ExecutionProgress progress, ExecutionStatus status) {
        if (progress.status() == status) {
            return progress;
        }

        return new ExecutionProgress(progress.id(), status, progress.done(), progress.total(), progress.percent(),
                null, progress.steps(), reasonFor(status, null, progress.currentStepName()));
    }

    // 실패 사유를 닫을 때 적은 판정으로 바꿔 단다. 실패 단계의 이름은 엔진이 준 단계 목록(nodes)에서 적은 단계 번호로 찾는다.
    // 엔진이 지금 가리키는 단계가 아니다 — 끊은 뒤에도 엔진은 다음 단계로 넘어가 있을 수 있다.
    public static ExecutionProgress judged(ExecutionProgress progress, ExecutionFailure judged,
                                           List<RunStatus.Node> nodes) {
        return new ExecutionProgress(progress.id(), progress.status(), progress.done(), progress.total(),
                progress.percent(), progress.currentStepName(), progress.steps(), Failure.of(judged, nodes));
    }

    private static Failure reasonFor(ExecutionStatus status, Failure reported, String currentStepName) {
        if (status != ExecutionStatus.FAILED || reported != null) {
            return reported;
        }

        return new Failure(currentStepName, UNREPORTED, null);
    }

    public static ExecutionProgress stalled(ExecutionId id, ExecutionProgress last, int stepCount) {
        return cut(id, last, stepCount, STALLED);
    }

    // 엔진이 실행을 모르고, 큐 대기 한도가 지나 다시 배달될 것도 없다. 플랫폼 탓으로 끝낸다.
    public static ExecutionProgress lost(ExecutionId id, ExecutionProgress last, int stepCount) {
        return cut(id, last, stepCount, LOST);
    }

    // 브로커가 실행 맡기기를 dead letter 로 보냈다. code 는 사유로 정한 실패 코드다.
    public static ExecutionProgress deadLettered(ExecutionId id, ExecutionProgress last, int stepCount, String code) {
        return cut(id, last, stepCount, code);
    }

    // 넘긴 채 큐에서 기다리다 dead letter 없이 사라져 큐 대기 안전망이 닫았다. 런타임이 받은 적이 없어 돈 단계가 없다.
    public static ExecutionProgress queueOverdue(ExecutionId id, int stepCount) {
        return cut(id, null, stepCount, QUEUE_EXPIRED);
    }

    private static ExecutionProgress cut(ExecutionId id, ExecutionProgress last, int stepCount, String code) {
        Failure failure = new Failure(last == null ? null : last.currentStepName(), code, "platform");

        if (last == null) {
            return new ExecutionProgress(id, ExecutionStatus.FAILED, 0, stepCount, 0, null, List.of(), failure);
        }

        return new ExecutionProgress(id, ExecutionStatus.FAILED, last.done(), last.total(), last.percent(),
                null, last.steps(), failure);
    }

    public static ExecutionStatus toStatus(RunState state) {
        return switch (state) {
            case QUEUED -> ExecutionStatus.QUEUED;
            case RUNNING -> ExecutionStatus.RUNNING;
            case SUCCEEDED -> ExecutionStatus.SUCCEEDED;
            case FAILED, UNKNOWN -> ExecutionStatus.FAILED;
        };
    }

    public record Step(String name, String status) {
        static Step of(RunStatus.Node node) {
            return new Step(node.name(), node.status());
        }
    }

    public record Failure(String stepName, String errorCode, String fault) {
        static Failure of(RunStatus.Failure failure, List<RunStatus.Node> nodes) {
            if (failure == null) {
                return null;
            }

            return new Failure(stepNameOf(failure.nodeId(), nodes), failure.errorCode(), failure.fault());
        }

        // 책임은 엔진이 알리는 모양(소문자)으로 답한다.
        static Failure of(ExecutionFailure judged, List<RunStatus.Node> nodes) {
            return new Failure(stepNameOf(judged.stepId(), nodes), judged.errorCode(),
                    judged.fault().name().toLowerCase(Locale.ROOT));
        }

        private static String stepNameOf(String nodeId, List<RunStatus.Node> nodes) {
            return nodes.stream()
                    .filter(node -> node.id() != null && node.id().equals(nodeId))
                    .map(RunStatus.Node::name)
                    .findFirst()
                    .orElse(null);
        }
    }
}
