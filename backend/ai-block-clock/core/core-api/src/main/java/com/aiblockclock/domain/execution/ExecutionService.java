package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {
    private final ExecutionPlanAssembler executionPlanAssembler;
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final ExecutionProgressStore progressStore;
    private final ExecutionMonitor executionMonitor;

    public Execution start(UserId requester, Workflow workflow, String comment, boolean mock) {
        ExecutionPlan plan = executionPlanAssembler.assemble(workflow);
        RunAccepted accepted = accept(RuntimeWorkflow.toJson(workflow, plan), comment, mock);

        Execution started = executionWriter.append(
                Execution.started(ExecutionId.newId(), workflow.getId(), requester,
                RunId.of(accepted.runId()), plan.promptCount())
        );

        return started;
    }

    public void beginWatching(Execution execution) {
        executionMonitor.begin(execution);
    }

    public boolean pollOnce(ExecutionId id) {
        return executionMonitor.poll(id);
    }

    public ExecutionProgress follow(ExecutionId id, UserId requester) {
        return progressOf(mine(id, requester));
    }

    public ExecutionProgress progressOf(Execution execution) {
        return progressStore.find(execution.getId()).orElseGet(() -> readThrough(execution));
    }

    public Execution mine(ExecutionId id, UserId requester) {
        Execution execution = executionReader.read(id);

        if (!execution.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.EXECUTION_NOT_FOUND);
        }

        return execution;
    }

    private ExecutionProgress readThrough(Execution execution) {
        if (execution.getStatus().isFinished()) {
            return concluded(execution);
        }

        ExecutionProgress progress = ExecutionProgress.of(
                execution.getId(), read(execution), execution.getPromptCount());

        if (progress.status() != execution.getStatus()) {
            executionWriter.moveTo(execution.getId(), progress.status());
        }

        return progress;
    }

    private ExecutionProgress concluded(Execution execution) {
        try {
            return ExecutionProgress.concluded(
                    ExecutionProgress.of(execution.getId(), aiRuntimeClient.read(execution.getRunId().value()),
                            execution.getPromptCount()),
                    execution.getStatus());
        } catch (AiClientException exception) {
            log.info("엔진이 끝난 실행을 잊었다. 우리가 남긴 것으로 답한다: execution={}", execution.getId().value());

            return ExecutionProgress.concluded(
                    execution.getId(), execution.getStatus(), execution.getPromptCount());
        }
    }

    private RunAccepted accept(String workflowJson, String comment, boolean mock) {
        try {
            return aiRuntimeClient.start(new RunRequest(workflowJson, null, comment, mock));
        } catch (AiClientException exception) {
            log.warn("실행을 맡기지 못했다: {}", exception.getMessage(), exception);

            throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
        }
    }

    private RunStatus read(Execution execution) {
        try {
            return aiRuntimeClient.read(execution.getRunId().value());
        } catch (AiClientException exception) {
            log.warn("진행 상황을 가져오지 못했다: {}", exception.getMessage(), exception);

            throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
        }
    }
}
