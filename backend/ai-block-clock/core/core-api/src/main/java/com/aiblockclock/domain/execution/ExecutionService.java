package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.node.RefFileStore;
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
    private final ExecutionReservationManager executionReservationManager;
    private final AiRuntimeClient aiRuntimeClient;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final ExecutionProgressStore progressStore;
    private final ExecutionMonitor executionMonitor;

    // 같은 요청 번호로 이미 접수했으면 다시 떼지 않고 그 실행을 돌려준다.
    // 새 요청이면 먼저 떼어 두고 엔진에 넘긴다. 잔액이 모자라면 엔진을 부르지 않는다.
    public Execution start(UserId requester, Workflow workflow, ExecutionRequestId requestId,
                           String comment, boolean mock, UploadedFile upload) {
        Optional<Execution> earlier = executionReader.find(requester, requestId);

        if (earlier.isPresent()) {
            return earlier.get();
        }

        if (upload != null) {
            executionFileValidator.validate(upload);
        }

        ExecutionPlan plan = executionPlanAssembler.assemble(workflow);
        ExecutionId id = ExecutionId.newId();
        ExecutionFile inputFile = upload == null ? null : executionFileStore.write(id, upload);
        Execution requested = Execution.requested(
                id, requestId, workflow.getId(), requester, plan.promptCount(), inputFile);

        try {
            AbcAmount reserved = executionReservationManager.reserve(requested, workflow);

            return dispatch(requested, reserved, RuntimeWorkflow.of(workflow, plan), comment, mock);
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

    // 엔진이 받지 않았으면 떼어 둔 금액을 돌려준다. 엔진이 받았는데 응답만 늦은 경우도
    // 지금 클라이언트는 같은 실패로 알려 구분할 수 없어, 실패로 보고 돌려준다.
    private Execution dispatch(Execution requested, AbcAmount reserved, RuntimeWorkflow runtime,
                               String comment, boolean mock) {
        RunAccepted accepted;

        try {
            accepted = aiRuntimeClient.start(new RunRequest(runtime.json(), null, comment,
                    pathOf(requested.getInputFile()), refFilePathsOf(runtime), mock));
        } catch (AiClientException exception) {
            log.warn("실행을 맡기지 못해 떼어 둔 금액을 돌려준다: {}", exception.getMessage(), exception);
            executionReservationManager.release(requested, reserved);

            throw new CoreException(ErrorType.EXECUTION_ENGINE_UNAVAILABLE);
        }

        return executionWriter.markDispatched(requested.getId(), RunId.of(accepted.runId()));
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
