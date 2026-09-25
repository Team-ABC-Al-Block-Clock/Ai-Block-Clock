package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.AbcBalanceReader;
import com.aiblockclock.domain.payment.LedgerTransaction;
import com.aiblockclock.domain.payment.LedgerTransactionWriter;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 실행 엔진 호출을 사이에 두고 앞(접수 + 선차감)과 뒤(되돌리기)에 쓰기가 있다.
// 엔진 호출은 트랜잭션 안에 둘 수 없어 Service 트랜잭션 하나로 묶지 못하므로, 앞과 뒤를 각각 한 트랜잭션으로 둔다.
// 외부 확인을 사이에 둔 충전 확정(ChargeConfirmationManager)과 같은 모양이다.
@Component
@RequiredArgsConstructor
public class ExecutionReservationManager {
    private final CostEstimateAssembler costEstimateAssembler;
    private final AbcBalanceReader abcBalanceReader;
    private final ExecutionWriter executionWriter;
    private final LedgerTransactionWriter ledgerTransactionWriter;

    // 실행을 먼저 적어 같은 요청 번호가 동시에 오면 늦은 쪽이 막히게 하고, 잔액을 잠근 채 확인한 뒤 예치로 옮긴다.
    // 모자라면 예외가 나서 실행 기록까지 함께 되돌려진다.
    @Transactional
    public AbcAmount reserve(Execution requested, Workflow workflow) {
        CostEstimate estimate = costEstimateAssembler.assemble(workflow);
        executionWriter.append(requested);

        CostQuote.of(estimate, abcBalanceReader.readForUpdate(requested.getRequester())).ensureExecutable();
        ledgerTransactionWriter.append(LedgerTransaction.forExecutionReserve(
                requested.getId().value(), requested.getRequester(), estimate.total()));

        return estimate.total();
    }

    // 엔진이 받지 않은 실행이다. 떼어 둔 금액을 돌려주고 실행은 실패로 남긴다.
    @Transactional
    public void release(Execution requested, AbcAmount reserved) {
        executionWriter.moveTo(requested.getId(), ExecutionStatus.FAILED);
        ledgerTransactionWriter.append(LedgerTransaction.forExecutionRelease(
                requested.getId().value(), requested.getRequester(), reserved));
    }
}
