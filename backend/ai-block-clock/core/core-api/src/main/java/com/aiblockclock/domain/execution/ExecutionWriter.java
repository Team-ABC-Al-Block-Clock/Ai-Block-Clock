package com.aiblockclock.domain.execution;

import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
@RequiredArgsConstructor
public class ExecutionWriter {
    // V17__add_execution_request_id.sql 의 제약 이름이다. 바꾸면 여기도 함께 바꾼다.
    private static final String REQUEST_UNIQUE_CONSTRAINT = "uq_execution_requester_request";

    private final ExecutionRepository executionRepository;

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
    public void moveTo(ExecutionId id, ExecutionStatus status) {
        entityOf(id).moveTo(status);
    }

    @Transactional
    public Execution markDispatched(ExecutionId id, RunId runId) {
        ExecutionEntity entity = entityOf(id);
        entity.markDispatched(runId.value());

        return ExecutionEntityConverter.toDomain(entity);
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
