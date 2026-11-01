package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionReader {
    private static final List<ExecutionStatus> UNFINISHED = List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING);

    private final ExecutionRepository executionRepository;
    private final ExecutionChargeRateRepository executionChargeRateRepository;
    private final ExecutionOutboxRepository executionOutboxRepository;

    public Execution read(ExecutionId id) {
        return ExecutionEntityConverter.toDomain(executionRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND)));
    }

    public Optional<Execution> find(UserId requester, ExecutionRequestId requestId) {
        return executionRepository.findByRequesterIdAndRequestId(requester.value(), requestId.value())
                .map(ExecutionEntityConverter::toDomain);
    }

    // 끝나지 않은 실행이다. 서버를 다시 띄우면 감시 목록이 비므로 복구가 도는 실행을 다시 감시에 붙이려고 읽는다.
    public List<UnfinishedExecution> readUnfinished() {
        return executionRepository.findAllByStatusIn(UNFINISHED)
                .stream()
                .map(entity -> new UnfinishedExecution(ExecutionEntityConverter.toDomain(entity), entity.getCreatedAt()))
                .toList();
    }

    // 아직 브로커로 넘기지 않은 실행이다(outbox 있음 · runId 없음 · 끝나지 않음). 접수한 순서대로다.
    public List<PendingDispatch> readPendingDispatches() {
        List<ExecutionOutboxEntity> outboxes = executionOutboxRepository.findUndispatched(UNFINISHED);
        Map<UUID, ExecutionEntity> executions = executionRepository.findAllById(
                        outboxes.stream().map(ExecutionOutboxEntity::getExecutionId).toList()).stream()
                .collect(Collectors.toMap(ExecutionEntity::getId, Function.identity()));

        return outboxes.stream()
                .map(outbox -> pendingOf(outbox, executions.get(outbox.getExecutionId())))
                .toList();
    }

    // 그 실행이 아직 넘기지 않은 실행이면 돌려준다. 이미 넘겼거나 끝났거나 outbox 가 없으면 비어 있다.
    public Optional<PendingDispatch> findPendingDispatch(ExecutionId id) {
        return executionOutboxRepository.findById(id.value())
                .filter(outbox -> outbox.getDispatchedAt() == null)
                .flatMap(outbox -> executionRepository.findById(id.value())
                        .filter(execution -> execution.getRunId() == null && !execution.getStatus().isFinished())
                        .map(execution -> pendingOf(outbox, execution)));
    }

    // 브로커 confirm 을 받아 넘긴 시각이다. 넘기기 전이거나 outbox 가 없는 옛 실행은 비어 있다.
    public Optional<Instant> readDispatchedAt(ExecutionId id) {
        return executionOutboxRepository.findById(id.value()).map(ExecutionOutboxEntity::getDispatchedAt);
    }

    // 실행을 닫을 때 적은 실패 판정(execution_failure)이다. 실패로 닫지 않았거나 판정을 적기 전의 옛 실행은 비어 있다.
    public Optional<ExecutionFailure> readFailure(ExecutionId id) {
        return executionRepository.findFailureById(id.value()).map(ExecutionReader::failureOf);
    }

    // 단가 칸이 생기기 전에 접수한 실행의 줄은 받을 값이 없어 싣지 않는다.
    public StepPrices readStepPrices(ExecutionId id) {
        return new StepPrices(executionChargeRateRepository.findAllByExecutionId(id.value()).stream()
                .filter(rate -> rate.getUnitPrice() != null)
                .collect(Collectors.toMap(ExecutionChargeRateEntity::getStepId,
                        rate -> new StepPrice(rate.getPriceMultiplier(), AbcAmount.of(rate.getUnitPrice())))));
    }

    // 접수한 시각은 outbox 를 적은 시각이다. 접수와 같은 트랜잭션에 적힌다.
    private static PendingDispatch pendingOf(ExecutionOutboxEntity outbox, ExecutionEntity execution) {
        return new PendingDispatch(ExecutionEntityConverter.toDomain(execution),
                RunMessages.readSubmit(outbox.getPayload().getBytes(StandardCharsets.UTF_8)), outbox.getCreatedAt());
    }

    private static ExecutionFailure failureOf(ExecutionFailureEntity entity) {
        return new ExecutionFailure(entity.getFailedStepId(), entity.getFailureCode(), entity.getFault(),
                entity.isAffectsSuccessRate(),
                entity.getSupplierId() == null ? null : UserId.of(entity.getSupplierId()),
                entity.getAgentId() == null ? null : AgentId.of(entity.getAgentId()));
    }
}
