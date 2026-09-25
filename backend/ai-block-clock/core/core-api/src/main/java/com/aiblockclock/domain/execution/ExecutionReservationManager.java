package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.payment.AbcBalanceReader;
import com.aiblockclock.domain.payment.LedgerTransaction;
import com.aiblockclock.domain.payment.LedgerTransactionWriter;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 실행 엔진 호출을 사이에 두고 앞(접수 + 선차감)과 뒤(되돌리기 · 끝난 뒤 환불)에 쓰기가 있다.
// 엔진 호출은 트랜잭션 안에 둘 수 없어 Service 트랜잭션 하나로 묶지 못하므로, 앞과 뒤를 각각 한 트랜잭션으로 둔다.
// 외부 확인을 사이에 둔 충전 확정(ChargeConfirmationManager)과 같은 모양이다.
@Component
@RequiredArgsConstructor
public class ExecutionReservationManager {
    private final CostEstimateAssembler costEstimateAssembler;
    private final AbcBalanceReader abcBalanceReader;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final LedgerTransactionWriter ledgerTransactionWriter;

    // 실행을 먼저 적어 같은 요청 번호가 동시에 오면 늦은 쪽이 막히게 하고, 잔액을 잠근 채 확인한 뒤 예치로 옮긴다.
    // 끝난 뒤 사용액을 셀 배율도 이때 함께 적는다. 모자라면 예외가 나서 실행 기록까지 함께 되돌려진다.
    @Transactional
    public Execution reserve(Execution requested, Workflow workflow, ExecutionPlan plan) {
        CostEstimate estimate = costEstimateAssembler.assemble(workflow);
        Execution reserved = executionWriter.append(requested.reserve(estimate.total()));
        executionWriter.appendChargeRates(reserved.getId(), ChargeRates.of(plan, estimate));

        CostQuote.of(estimate, abcBalanceReader.readForUpdate(reserved.getRequester())).ensureExecutable();
        ledgerTransactionWriter.append(LedgerTransaction.forExecutionReserve(
                reserved.getId().value(), reserved.getRequester(), reserved.getReserved()));

        return reserved;
    }

    // 엔진이 받지 않은 실행이다. 떼어 둔 금액을 돌려주고 실행은 실패로 남긴다.
    @Transactional
    public void release(Execution reserved) {
        executionWriter.moveTo(reserved.getId(), ExecutionStatus.FAILED);
        ledgerTransactionWriter.append(LedgerTransaction.forExecutionRelease(
                reserved.getId().value(), reserved.getRequester(), reserved.getReserved()));
    }

    // 실행을 닫는 자리는 여기 하나다. 뒤에서 따라가는 모니터도, 모니터가 놓친 실행을 조회가 볼 때도 여기로 온다.
    // 닫기와 환불이 한 트랜잭션이라 닫혔는데 돌려주지 않은 실행이 남지 않는다.
    // 이미 닫힌 실행이면 아무것도 하지 않아, 모니터와 조회가 겹쳐도 한 번만 돌려준다.
    // run 은 엔진이 알린 마지막 상태다. 기한 안에 끝을 알리지 않아 우리가 끊은 실행은 비어 있다.
    @Transactional
    public void finish(ExecutionId id, ExecutionStatus status, RunStatus run) {
        Optional<Execution> closed = executionWriter.finish(id, status);

        if (closed.isEmpty() || !closed.get().isReserved()) {
            return;
        }

        Execution execution = closed.get();
        ExecutionUsage usage = ExecutionUsage.of(execution.getReserved(), run, executionReader.readChargeRates(id));

        if (usage.hasRefund()) {
            ledgerTransactionWriter.append(LedgerTransaction.forExecutionRefund(
                    id.value(), execution.getRequester(), AbcAmount.of(usage.refund())));
        }
    }
}
