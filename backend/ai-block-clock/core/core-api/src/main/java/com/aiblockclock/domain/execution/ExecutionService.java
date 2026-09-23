package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionStatus;
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

    public Execution start(UserId requester, Workflow workflow, String comment, boolean mock) {
        ExecutionPlan plan = executionPlanAssembler.assemble(workflow);
        RunAccepted accepted = accept(RuntimeWorkflow.toJson(workflow, plan), comment, mock);

        return executionWriter.append(Execution.started(
                ExecutionId.newId(), workflow.getId(), requester,
                RunId.of(accepted.runId()), plan.promptCount()));
    }

    public ExecutionProgress follow(ExecutionId id, UserId requester) {
        Execution execution = executionReader.read(id);

        if (!execution.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.EXECUTION_NOT_FOUND);
        }

        RunStatus run = read(execution);
        ExecutionStatus reported = ExecutionProgress.toStatus(run.state());

        if (reported != execution.getStatus()) {
            executionWriter.moveTo(id, reported);
        }

        return ExecutionProgress.of(id, run, execution.getPromptCount());
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
