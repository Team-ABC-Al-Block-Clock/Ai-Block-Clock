package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 실행은 브로커로 맡기고 이벤트로 따라간다(ADR-0001). 사용자 요청은 DB 에만 쓰고, 엔진도 브로커도 부르지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {
    private final ExecutionPlanAssembler executionPlanAssembler;
    private final ExecutionFileValidator executionFileValidator;
    private final ExecutionInputValidator executionInputValidator;
    private final ExecutionFileStore executionFileStore;
    private final ExecutionReservationManager executionReservationManager;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final ExecutionTracker executionTracker;
    private final ExecutionResultCollector executionResultCollector;
    private final ExecutionResultReader executionResultReader;
    private final ExecutionResultStore executionResultStore;
    private final ExecutionProgressStore progressStore;
    private final AiRuntimeClient aiRuntimeClient;
    private final Clock clock;

    // 같은 요청 번호로 이미 접수했으면 다시 떼지 않고 그 실행을 돌려준다.
    // 새 요청이면 실행 · 선차감 · 보낼 SubmitRun(outbox)을 한 트랜잭션에 적는다. 넘기는 일은 커밋된 뒤 디스패처가 한다 —
    // 브로커가 죽어 있거나 느려도 접수는 된다. 잔액이 모자라면 셋 다 남지 않고 올린 파일도 지운다.
    @Transactional
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
            Execution requested = Execution.requested(
                    id, requestId, workflow.getId(), requester, plan.promptCount(), inputFile, mock);
            Execution reserved = executionReservationManager.reserve(requested, workflow, plan);

            executionWriter.appendOutbox(id, RunSubmission.forRun(id, RuntimeWorkflow.of(workflow, plan), checkedInput,
                    inputFile, comment, mock, clock.instant()));

            return ExecutionStart.newlyCreated(reserved);
        } catch (RuntimeException exception) {
            executionFileStore.erase(inputFile);

            throw exception;
        }
    }

    // 감시에 다시 붙인다(복구). 이미 감시 중이거나 아직 큐에서 기다리는 실행이면 false 다.
    public boolean beginWatching(Execution execution) {
        return executionTracker.begin(execution);
    }

    public List<UnfinishedExecution> readUnfinished() {
        return executionReader.readUnfinished();
    }

    // abc.events.backend 로 온 실행(mode=run)의 이벤트다.
    public void applyEvent(RunEvent event) {
        executionTracker.apply(event);
    }

    // abc.runs.dead 로 온 실행(mode=run)이다. 플랫폼 탓으로 닫는다.
    public void closeDeadLettered(ExecutionId id, DeadLetterReason reason) {
        executionTracker.closeDeadLettered(id, reason);
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

    // 진행 저장소에 없는 실행이다(재기동 직후 · 이벤트가 아직 없음). 끝나지 않은 실행은 엔진에 묻지도 닫지도 않고
    // DB 상태로 답한다 — 이벤트가 다시 오거나 감시가 닫는다. 끝난 실행은 엔진이 아직 들고 있으면 단계까지 보여 준다.
    // 실패로 끝난 실행의 사유는 닫을 때 적은 판정이다. 엔진이 모르거나(dead letter · LOST) 다르게 답해도(STALLED 로 끊음)
    // 돈은 그 판정으로 정리했다. 판정이 남지 않은 옛 실행은 까닭을 모르는 실패로 답한다.
    private ExecutionProgress readThrough(Execution execution) {
        if (!execution.getStatus().isFinished()) {
            return recorded(execution);
        }

        Optional<RunStatus> run = execution.isDispatched() ? readRun(execution) : Optional.empty();
        ExecutionProgress progress = run
                .map(found -> ExecutionProgress.concluded(
                        ExecutionProgress.of(execution.getId(), found, execution.getPromptCount()),
                        execution.getStatus()))
                .orElseGet(() -> recorded(execution));

        if (execution.getStatus() != ExecutionStatus.FAILED) {
            return progress;
        }

        List<RunStatus.Node> nodes = run.map(RunStatus::nodes).orElse(List.of());

        return executionReader.readFailure(execution.getId())
                .map(judged -> ExecutionProgress.judged(progress, judged, nodes))
                .orElse(progress);
    }

    private Optional<RunStatus> readRun(Execution execution) {
        try {
            return Optional.of(aiRuntimeClient.read(execution.getRunId().value()));
        } catch (AiClientException exception) {
            log.info("엔진이 끝난 실행을 잊었다. 우리가 남긴 것으로 답한다: execution={}", execution.getId().value());

            return Optional.empty();
        }
    }

    private static ExecutionProgress recorded(Execution execution) {
        return ExecutionProgress.recorded(execution.getId(), execution.getStatus(), execution.getPromptCount());
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
                .or(() -> collectAgain(id, format))
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_RESULT_NOT_FOUND,
                        Map.of("format", format.name())));
    }

    public Path locate(ExecutionResult result) {
        return executionResultStore.locate(result);
    }

    // 닫을 때 보관하지 못한 결과물이다(볼륨이 늦게 붙었다 · 서버가 그 사이 내려갔다). 결과 볼륨에서 다시 가져온다.
    private Optional<ExecutionResult> collectAgain(ExecutionId id, ExecutionResultFormat format) {
        executionResultCollector.collect(id);

        return executionResultReader.read(id, format);
    }
}
