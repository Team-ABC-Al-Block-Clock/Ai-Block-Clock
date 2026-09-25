package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ExecutionWriter {
    // V17__add_execution_request_id.sql 의 제약 이름이다. 바꾸면 여기도 함께 바꾼다.
    private static final String REQUEST_UNIQUE_CONSTRAINT = "uq_execution_requester_request";

    private final ExecutionRepository executionRepository;
    private final ExecutionChargeRateRepository executionChargeRateRepository;
    private final ExecutionSettlementRepository executionSettlementRepository;

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
    public void appendChargeRates(ExecutionId id, ChargeRates rates) {
        executionChargeRateRepository.saveAll(rates.byStep().entrySet().stream()
                .map(rate -> new ExecutionChargeRateEntity(id.value(), rate.getKey(), rate.getValue()))
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

    @Transactional
    public void moveTo(ExecutionId id, ExecutionStatus status) {
        entityOf(id).moveTo(status);
    }

    // 실행 행을 잠그고 끝난 상태로 옮겨 닫은 실행을 돌려준다. 이미 끝나 있으면 옮기지 않고 비워 돌려준다.
    // 모니터와 조회가 같은 끝을 동시에 봐도 늦은 쪽은 앞쪽이 커밋할 때까지 기다렸다가 끝난 것을 본다.
    // 실패했으면 누구 탓인지도 함께 적는다. 성공한 실행은 failure 가 비어 있다.
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Execution> finish(ExecutionId id, ExecutionStatus status, ExecutionFailure failure) {
        ExecutionEntity entity = executionRepository.findForUpdate(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));

        if (entity.getStatus().isFinished()) {
            return Optional.empty();
        }

        entity.moveTo(status);

        if (failure != null) {
            record(entity, failure);
        }

        return Optional.of(ExecutionEntityConverter.toDomain(entity));
    }

    // 엔진에 넘기지 못한 실행을 실패로 닫는다. 돌지 않은 실행이라 모니터 · 조회와 겹치지 않는다.
    @Transactional
    public void fail(ExecutionId id, ExecutionFailure failure) {
        ExecutionEntity entity = entityOf(id);
        entity.moveTo(ExecutionStatus.FAILED);
        record(entity, failure);
    }

    @Transactional
    public Execution markDispatched(ExecutionId id, RunId runId) {
        ExecutionEntity entity = entityOf(id);
        entity.markDispatched(runId.value());

        return ExecutionEntityConverter.toDomain(entity);
    }

    private static void record(ExecutionEntity entity, ExecutionFailure failure) {
        entity.recordFailure(failure.stepId(), failure.errorCode(), failure.fault(), failure.affectsSuccessRate(),
                failure.supplier() == null ? null : failure.supplier().value(),
                failure.agent() == null ? null : failure.agent().value());
    }

    private ExecutionEntity entityOf(ExecutionId id) {
        return executionRepository.findById(id.value())
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
