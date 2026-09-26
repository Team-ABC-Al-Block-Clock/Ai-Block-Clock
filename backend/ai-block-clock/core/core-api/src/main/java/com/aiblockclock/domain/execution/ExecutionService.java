package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.node.RefFileStore;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunMode;
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
    private final ExecutionInputValidator executionInputValidator;
    private final ExecutionFileStore executionFileStore;
    private final RefFileStore refFileStore;
    private final ExecutionReservationManager executionReservationManager;
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final ExecutionResultHarvester executionResultHarvester;
    private final ExecutionResultReader executionResultReader;
    private final ExecutionResultStore executionResultStore;
    private final ExecutionProgressStore progressStore;
    private final ExecutionMonitor executionMonitor;

    // 같은 요청 번호로 이미 접수했으면 다시 떼지 않고 그 실행을 돌려준다.
    // 새 요청이면 엔진에 보낼 요청을 다 만든 뒤에 떼어 두고 엔진에 넘긴다. 잔액이 모자라면 엔진을 부르지 않는다.
    public ExecutionStart start(UserId requester, Workflow workflow, ExecutionRequestId requestId,
                                String input, String comment, boolean mock, UploadedFile upload) {
        Optional<Execution> earlier = executionReader.find(requester, requestId);

        if (earlier.isPresent()) {
            return ExecutionStart.alreadyRequested(earlier.get());
        }

        if (upload != null) {
            executionFileValidator.validate(upload);
        }

        String checkedInput = executionInputValidator.validate(workflow.getGraph(), input);
        ExecutionPlan plan = executionPlanAssembler.assemble(workflow);
        ExecutionId id = ExecutionId.newId();
        ExecutionFile inputFile = upload == null ? null : executionFileStore.write(id, upload);

        try {
            RunRequest runRequest = runRequestOf(RuntimeWorkflow.of(workflow, plan), checkedInput, inputFile, comment,
                    mock);
            Execution requested = Execution.requested(
                    id, requestId, workflow.getId(), requester, plan.promptCount(), inputFile, mock);
            Execution reserved = executionReservationManager.reserve(requested, workflow, plan);

            return ExecutionStart.newlyCreated(dispatch(reserved, runRequest));
        } catch (RuntimeException exception) {
            executionFileStore.erase(inputFile);

            throw exception;
        }
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

    // 엔진에 넘기기 전이거나 넘기지 못한 실행은 엔진에 물을 번호가 없다. 우리가 남긴 것으로 답한다.
    private ExecutionProgress readThrough(Execution execution) {
        if (!execution.isDispatched()) {
            return ExecutionProgress.concluded(execution.getId(), execution.getStatus(), execution.getPromptCount());
        }

        if (execution.getStatus().isFinished()) {
            return concluded(execution);
        }

        RunStatus run = read(execution);
        ExecutionProgress progress = ExecutionProgress.of(execution.getId(), run, execution.getPromptCount());

        // 뒤에서 따라가던 모니터가 닫지 못한 실행이다. 모니터와 같은 자리에서 닫아야 쓰지 않은 만큼이 돌아온다.
        if (progress.status().isFinished()) {
            executionReservationManager.finish(execution.getId(), progress.status(), run);

            if (progress.status() == ExecutionStatus.SUCCEEDED) {
                executionResultHarvester.harvest(execution);
            }
        } else if (progress.status() != execution.getStatus()) {
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

    public ExecutionResult download(ExecutionId id, UserId requester, ExecutionResultFormat format) {
        Execution execution = executionReader.read(id);

        if (!execution.isRequestedBy(requester)) {
            throw new CoreException(ErrorType.EXECUTION_NOT_FOUND);
        }

        if (!execution.getStatus().isFinished()) {
            throw new CoreException(ErrorType.EXECUTION_RESULT_NOT_READY);
        }

        if (execution.getStatus() == ExecutionStatus.FAILED) {
            throw new CoreException(ErrorType.EXECUTION_RESULT_UNAVAILABLE);
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

    // 떼어 둔 뒤에는 무엇이 실패하든 돌려준다. 실행 번호를 남기지 못한 경우는 엔진이 이미 받았지만,
    // 사용자 돈을 묶어 두기보다 돌려주는 쪽을 택한다. 엔진이 받았는데 응답만 늦은 경우도 지금은 구분할 수 없다.
    private Execution dispatch(Execution reserved, RunRequest runRequest) {
        try {
            RunAccepted accepted = aiRuntimeClient.start(runRequest);

            return executionWriter.markDispatched(reserved.getId(), RunId.of(accepted.runId()));
        } catch (RuntimeException exception) {
            log.warn("실행을 맡기지 못해 떼어 둔 금액을 돌려준다: execution={}", reserved.getId().value(), exception);
            release(reserved, exception);

            if (exception instanceof AiClientException) {
                throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
            }

            throw exception;
        }
    }

    // 돌려주기마저 실패하면 원래 실패를 가리지 않도록 덧붙여 두고, 묶인 실행을 찾을 수 있게 남긴다.
    private void release(Execution reserved, RuntimeException cause) {
        try {
            executionReservationManager.release(reserved);
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
            log.error("떼어 둔 금액을 돌려주지 못했다: execution={}", reserved.getId().value(), failure);
        }
    }

    private RunRequest runRequestOf(RuntimeWorkflow runtime, String input, ExecutionFile inputFile, String comment,
                                    boolean mock) {
        return new RunRequest(runtime.json(), input, comment, pathOf(inputFile), refFilePathsOf(runtime), mock,
                RunMode.RUN);
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
