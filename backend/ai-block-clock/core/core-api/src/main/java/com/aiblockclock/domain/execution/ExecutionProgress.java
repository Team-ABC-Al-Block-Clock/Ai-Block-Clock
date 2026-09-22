package com.aiblockclock.domain.execution;

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
        List<Step> steps
) {
    public static ExecutionProgress of(ExecutionId id, RunStatus run, int promptCount) {
        RunStatus.Progress progress = run.progress();

        return new ExecutionProgress(
                id,
                toStatus(run.status()),
                progress == null ? 0 : progress.done(),
                progress == null ? promptCount : progress.total(),
                progress == null ? 0 : progress.percent(),
                progress == null ? null : progress.currentNodeName(),
                run.nodes() == null ? List.of() : run.nodes().stream().map(Step::of).toList());
    }

    public static ExecutionStatus toStatus(String runStatus) {
        return ExecutionStatus.valueOf(runStatus.toUpperCase(Locale.ROOT));
    }

    public record Step(String name, String status) {
        static Step of(RunStatus.Node node) {
            return new Step(node.name(), node.status());
        }
    }
}
