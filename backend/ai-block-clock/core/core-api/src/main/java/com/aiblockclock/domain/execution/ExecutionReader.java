package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionReader {
    private final ExecutionRepository executionRepository;
    private final ExecutionChargeRateRepository executionChargeRateRepository;

    public Execution read(ExecutionId id) {
        return ExecutionEntityConverter.toDomain(executionRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND)));
    }

    public Optional<Execution> find(UserId requester, ExecutionRequestId requestId) {
        return executionRepository.findByRequesterIdAndRequestId(requester.value(), requestId.value())
                .map(ExecutionEntityConverter::toDomain);
    }

    // 단가 칸이 생기기 전에 접수한 실행의 줄은 받을 값이 없어 싣지 않는다.
    public StepPrices readStepPrices(ExecutionId id) {
        return new StepPrices(executionChargeRateRepository.findAllByExecutionId(id.value()).stream()
                .filter(rate -> rate.getUnitPrice() != null)
                .collect(Collectors.toMap(ExecutionChargeRateEntity::getStepId,
                        rate -> new StepPrice(rate.getPriceMultiplier(), AbcAmount.of(rate.getUnitPrice())))));
    }
}
