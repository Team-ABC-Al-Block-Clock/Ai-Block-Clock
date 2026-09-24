package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.node.RefFileStore;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {
    private final ExecutionPlanAssembler executionPlanAssembler;
    private final ExecutionFileValidator executionFileValidator;
    private final ExecutionFileStore executionFileStore;
    private final RefFileStore refFileStore;
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final ExecutionResultHarvester executionResultHarvester;
    private final ExecutionResultReader executionResultReader;
    private final ExecutionResultStore executionResultStore;

    public Execution start(
            UserId requester, Workflow workflow, String comment, boolean mock, UploadedFile upload) {
        if (upload != null) {
            executionFileValidator.validate(upload);
        }

        ExecutionPlan plan = executionPlanAssembler.assemble(workflow);
        ExecutionId id = ExecutionId.newId();
        ExecutionFile inputFile = upload == null ? null : executionFileStore.write(id, upload);

        try {
            RunAccepted accepted = accept(RuntimeWorkflow.of(workflow, plan), comment, mock, inputFile);

            return executionWriter.append(Execution.started(
                    id, workflow.getId(), requester, RunId.of(accepted.runId()), plan.promptCount(), inputFile));
        } catch (RuntimeException exception) {
            executionFileStore.erase(inputFile);

            throw exception;
        }
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

            if (reported == ExecutionStatus.SUCCEEDED) {
                executionResultHarvester.harvest(execution);
            }
        }

        return ExecutionProgress.of(id, run, execution.getPromptCount());
    }

    public ExecutionResult download(ExecutionId id, UserId requester, ExecutionResultFormat format) {
        Execution execution = executionReader.read(id);

        if (!execution.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.EXECUTION_NOT_FOUND);
        }

        if (execution.getStatus() != ExecutionStatus.SUCCEEDED) {
            throw new CoreException(ErrorType.EXECUTION_RESULT_NOT_READY);
        }

        return executionResultReader.read(id, format)
                .or(() -> retry(execution, format))
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_RESULT_NOT_FOUND,
                        Map.of("format", format.name())));
    }

    public Path locate(ExecutionResult result) {
        return executionResultStore.locate(result);
    }

    private Optional<ExecutionResult> retry(Execution execution, ExecutionResultFormat format) {
        executionResultHarvester.harvest(execution);

        return executionResultReader.read(execution.getId(), format);
    }

    private RunAccepted accept(
            RuntimeWorkflow runtime, String comment, boolean mock, ExecutionFile inputFile) {
        try {
            return aiRuntimeClient.start(new RunRequest(runtime.json(), null, comment,
                    pathOf(inputFile), refFilePathsOf(runtime), mock));
        } catch (AiClientException exception) {
            log.warn("실행을 맡기지 못했다: {}", exception.getMessage(), exception);

            throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
        }
    }

    private Path pathOf(ExecutionFile inputFile) {
        return inputFile == null ? null : executionFileStore.locate(inputFile);
    }

    private Map<String, List<Path>> refFilePathsOf(RuntimeWorkflow runtime) {
        return runtime.refFiles().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(refFileStore::locate).toList()));
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
