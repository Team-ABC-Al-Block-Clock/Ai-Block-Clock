package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionRunRecordRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.execution.ExecutionStepRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionEvidenceReader {
    // LedgerTransaction.forExecution* 가 실행 거래에 붙이는 참조 종류다.
    private static final String EXECUTION_REFERENCE_TYPE = "EXECUTION";

    private final ExecutionRepository executionRepository;
    private final ExecutionFailureRepository executionFailureRepository;
    private final ExecutionChargeRateRepository executionChargeRateRepository;
    private final ExecutionStepRepository executionStepRepository;
    private final ExecutionSettlementRepository executionSettlementRepository;
    private final ExecutionRunRecordRepository executionRunRecordRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ExecutionEvidence read(ExecutionId id) {
        UUID key = id.value();
        ExecutionEntity execution = executionRepository.findById(key)
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_NOT_FOUND));

        return new ExecutionEvidence(
                summaryOf(execution),
                executionFailureRepository.findById(key)
                        .map(failure -> new ExecutionEvidence.Failure(failure.getFailedStepId(),
                                failure.getFailureCode(), failure.getFault().name(), failure.isAffectsSuccessRate(),
                                failure.getSupplierId(), failure.getAgentId()))
                        .orElse(null),
                executionChargeRateRepository.findAllByExecutionId(key).stream()
                        .map(rate -> new ExecutionEvidence.ChargeRate(rate.getStepId(), rate.getPriceMultiplier(),
                                rate.getUnitPrice()))
                        .toList(),
                executionStepRepository.findAllByExecutionIdOrderByIdAsc(key).stream()
                        .map(step -> new ExecutionEvidence.Step(step.getStepId(), step.getNodeName(),
                                step.getStepType(), step.getStatus(), step.getSkipReason(), step.getBranchTaken(),
                                step.getOwnerId(), step.getAgentId(), step.getModel(), step.getInputTokens(),
                                step.getOutputTokens(), step.getRetries(), step.getRepairCount(), step.getCostUsd(),
                                step.getStartedAt(), step.getFinishedAt(), step.getDurationMs(), step.getUnitPrice(),
                                step.getSettledAbc()))
                        .toList(),
                executionSettlementRepository.findAllByExecutionIdOrderByIdAsc(key).stream()
                        .map(settlement -> new ExecutionEvidence.Settlement(settlement.getStepId(),
                                settlement.getNodeName(), settlement.getSupplierId(), settlement.getAgentId(),
                                settlement.getAmountAbc(), settlement.getCreatedAt()))
                        .toList(),
                ledgerOf(key),
                executionRunRecordRepository.findById(key)
                        .map(record -> new ExecutionEvidence.RunRecord(record.getClosedBy(), record.getRunId(),
                                record.getEngineStatus(), record.getResponse(), record.getResponseSha256(),
                                record.getNote(), record.getRecordedAt()))
                        .orElse(null));
    }

    private static ExecutionEvidence.Summary summaryOf(ExecutionEntity execution) {
        return new ExecutionEvidence.Summary(execution.getId(), execution.getRequesterId(),
                execution.getWorkflowId(), execution.getStatus().name(), execution.getRunId(), execution.isMock(),
                execution.getReservedAbc(), execution.getCreatedAt(), execution.getUpdatedAt());
    }

    private List<ExecutionEvidence.LedgerTransaction> ledgerOf(UUID executionId) {
        return ledgerTransactionRepository
                .findAllByReferenceTypeAndReferenceIdOrderByIdAsc(EXECUTION_REFERENCE_TYPE, executionId.toString())
                .stream()
                .map(transaction -> new ExecutionEvidence.LedgerTransaction(
                        transaction.getEventType().name(),
                        transaction.getIdempotencyKey(),
                        transaction.getCreatedAt(),
                        ledgerEntryRepository.findAllByTransactionIdOrderByIdAsc(transaction.getId()).stream()
                                .map(entry -> new ExecutionEvidence.LedgerEntry(
                                        entry.getAccount().getAccountType().name(),
                                        entry.getAccount().getReferenceId(),
                                        entry.getAmountDelta()))
                                .toList()))
                .toList();
    }
}
