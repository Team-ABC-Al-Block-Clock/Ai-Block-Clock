package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunMessages;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionOutboxEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExecutionWriter {
    // V7__create_execution.sql 의 제약 이름이다. 바꾸면 여기도 함께 바꾼다.
    private static final String REQUEST_UNIQUE_CONSTRAINT = "uq_execution_requester_request";

    private final ExecutionRepository executionRepository;
    private final ExecutionChargeRateRepository executionChargeRateRepository;
    private final ExecutionFailureRepository executionFailureRepository;
    private final ExecutionSettlementRepository executionSettlementRepository;
    private final ExecutionOutboxRepository executionOutboxRepository;
    private final ApplicationEventPublisher events;

    // 같은 요청 번호가 동시에 두 번 들어오면 늦은 쪽이 여기서 막힌다.
    // 제약 위반을 이 자리에서 알아채려고 SQL 을 곧바로 내보낸다. 트랜잭션이 실패하면 함께 되돌려진다.
    @Transactional
    public Execution append(Execution execution) {
        try {
            return ExecutionEntityConverter.toDomain(
                    executionRepository.saveAndFlush(ExecutionEntityConverter.toEntity(execution)));
        } catch (DataIntegrityViolationException exception) {
            if (!isDuplicateRequest(exception)) {
                throw exception;
            }

            throw new CoreException(ErrorType.EXECUTION_ALREADY_REQUESTED);
        }
    }

    @Transactional
    public void appendStepPrices(ExecutionId id, StepPrices prices) {
        executionChargeRateRepository.saveAll(prices.byStep().entrySet().stream()
                .map(step -> new ExecutionChargeRateEntity(id.value(), step.getKey(),
                        step.getValue().multiplier(), step.getValue().price().value()))
                .toList());
    }

    // 실행을 닫는 트랜잭션(finish) 안에서만 적는다. 닫기가 한 번뿐이라 정산 내역도 한 번만 남는다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendSettlements(ExecutionId id, List<ExecutionSettlement> settlements) {
        executionSettlementRepository.saveAll(settlements.stream()
                .map(settlement -> new ExecutionSettlementEntity(id.value(), settlement.stepId(), settlement.nodeName(),
                        settlement.supplier().value(),
                        settlement.agent() == null ? null : settlement.agent().value(),
                        settlement.amount().value()))
                .toList());
    }

    // 행을 잠근 뒤 끝난 실행이면 옮기지 않는다. 이벤트를 적용하는 사이 감시 · dead letter 가 먼저 닫았을 수 있어,
    // 닫힌 실행이 진행 중으로 되돌아가지 않게 한다.
    @Transactional
    public void moveTo(ExecutionId id, ExecutionStatus status) {
        ExecutionEntity entity = lock(id);

        if (!entity.getStatus().isFinished()) {
            entity.moveTo(status);
        }
    }

    // 실행 행을 잠그고 끝난 상태로 옮겨 닫은 실행을 돌려준다. 이미 끝나 있으면 옮기지 않고 비워 돌려준다.
    // 이벤트 · 감시 · dead letter 가 같은 실행을 동시에 닫아도 늦은 쪽은 앞쪽이 커밋할 때까지 기다렸다가 끝난 것을 본다.
    // 실패했으면 닫은 뒤 바로 누구 탓인지를 적는다. 성공한 실행은 failure 가 비어 있다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Execution> finish(ExecutionId id, ExecutionStatus status, ExecutionFailure failure) {
        ExecutionEntity entity = executionRepository.findForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));

        if (entity.getStatus().isFinished()) {
            return Optional.empty();
        }

        entity.moveTo(status);

        if (failure != null) {
            executionFailureRepository.save(toEntity(id, failure));
        }

        return Optional.of(ExecutionEntityConverter.toDomain(entity));
    }

    // 아직 브로커에 넘기지 않은 실행만 닫는다. 행을 잠근 뒤에 실행 번호를 보므로, 커밋 직후 시도가 늦게 넘긴 실행을
    // 디스패처가 넘기지 못한 것으로 잘못 보고 닫는 일이 없다. 넘겼거나 이미 끝났으면 비워 돌려준다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Execution> finishUndispatched(ExecutionId id, ExecutionStatus status, ExecutionFailure failure) {
        ExecutionEntity entity = executionRepository.findForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));

        if (entity.getRunId() != null) {
            return Optional.empty();
        }

        return finish(id, status, failure);
    }

    // 큐에서 기다리는 실행만 닫는다(큐 대기 안전망). 행을 잠근 뒤에 상태를 보므로, 안전망이 다시 읽은 뒤 accepted 가 와서
    // RUNNING 이 된 실행을 큐에서 사라진 것으로 잘못 보고 닫는 일이 없다. QUEUED 가 아니면(RUNNING · 끝남) 비워 돌려준다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Execution> finishQueued(ExecutionId id, ExecutionStatus status, ExecutionFailure failure) {
        if (lock(id).getStatus() != ExecutionStatus.QUEUED) {
            return Optional.empty();
        }

        return finish(id, status, failure);
    }

    // 접수와 같은 트랜잭션에 브로커로 보낼 SubmitRun 을 적는다. 커밋되면 디스패처가 곧바로 넘긴다(ExecutionAccepted) —
    // 되돌려진 접수는 알리지 않는다. 커밋 직후 시도가 놓친 것은 디스패처의 주기 점검이 이 줄을 보고 다시 넘긴다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void appendOutbox(ExecutionId id, SubmitRun submit) {
        executionOutboxRepository.save(new ExecutionOutboxEntity(id.value(), RunMessages.toJson(submit)));
        events.publishEvent(new ExecutionAccepted(id));
    }

    // 행을 잠근 뒤 아직 넘긴 기록이 없고 끝나지 않은 실행에만 실행 번호를 적고 true 다. 넘기는 쪽이 둘(디스패처의 두 시도 ·
    // 이벤트)이라 겹쳐도 한 번만 적히고, 넘기지 못해 버려진 실행에 늦게 온 confirm 이 넘긴 기록을 되살리지 않는다.
    @Transactional
    public boolean markDispatched(ExecutionId id, RunId runId) {
        ExecutionEntity entity = lock(id);

        if (entity.getRunId() != null || entity.getStatus().isFinished()) {
            return false;
        }

        entity.markDispatched(runId.value());

        return true;
    }

    // 넘긴 시각은 감시가 큐 대기 한도를 잴 때 쓴다. outbox 가 생기기 전에 접수한 실행에는 적을 곳이 없다.
    @Transactional(propagation = Propagation.MANDATORY)
    public void markOutboxDispatched(ExecutionId id, Instant dispatchedAt) {
        executionOutboxRepository.findById(id.value()).ifPresent(outbox -> outbox.markDispatched(dispatchedAt));
    }

    private static ExecutionFailureEntity toEntity(ExecutionId id, ExecutionFailure failure) {
        return new ExecutionFailureEntity(id.value(), failure.stepId(), failure.errorCode(), failure.fault(),
                failure.affectsSuccessRate(),
                failure.supplier() == null ? null : failure.supplier().value(),
                failure.agent() == null ? null : failure.agent().value());
    }

    private ExecutionEntity lock(ExecutionId id) {
        return executionRepository.findForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));
    }

    private boolean isDuplicateRequest(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraintName = violation.getConstraintName();

                // DB 가 제약 이름에 접두 · 접미 표현을 붙일 수 있어 대소문자를 맞춘 뒤 포함 여부로 본다.
                return constraintName != null
                        && constraintName.toLowerCase(Locale.ROOT).contains(REQUEST_UNIQUE_CONSTRAINT);
            }
        }

        return false;
    }
}
