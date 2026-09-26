package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunState;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.enums.execution.ExecutionStatus;

import java.util.List;

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

    public static ExecutionProgress of(ExecutionId id, RunStatus run, int promptCount) {
        RunStatus.Progress progress = run.progress();
        List<RunStatus.Node> nodes = run.nodes() == null ? List.of() : run.nodes();
        ExecutionStatus status = toStatus(run.state());
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

    public static ExecutionProgress concluded(ExecutionId id, ExecutionStatus status, int stepCount) {
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

    private static Failure reasonFor(ExecutionStatus status, Failure reported, String currentStepName) {
        if (status != ExecutionStatus.FAILED || reported != null) {
            return reported;
        }

        return new Failure(currentStepName, UNREPORTED, null);
    }

    public static ExecutionProgress queued(ExecutionId id, int stepCount) {
        return new ExecutionProgress(id, ExecutionStatus.QUEUED, 0, stepCount, 0, null, List.of(), null);
    }

    public static ExecutionProgress stalled(ExecutionId id, ExecutionProgress last, int stepCount) {
        Failure failure = new Failure(last == null ? null : last.currentStepName(), STALLED, "platform");

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

        private static String stepNameOf(String nodeId, List<RunStatus.Node> nodes) {
            return nodes.stream()
                    .filter(node -> node.id() != null && node.id().equals(nodeId))
                    .map(RunStatus.Node::name)
                    .findFirst()
                    .orElse(null);
        }
    }
}
