package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentGradeMetricWriter;
import com.aiblockclock.domain.payment.AbcBalanceReader;
import com.aiblockclock.domain.payment.LedgerTransaction;
import com.aiblockclock.domain.payment.LedgerTransactionWriter;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.enums.execution.ExecutionStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

// 브로커로 넘기기를 사이에 두고 앞(접수 + 선차감)과 뒤(넘긴 기록 · 되돌리기 · 끝난 뒤 환불)에 쓰기가 있다.
// 발행은 트랜잭션 안에 둘 수 없어 하나로 묶지 못하므로, 단계마다 한 트랜잭션으로 둔다. 접수는 실행 Service 의
// 트랜잭션(outbox 와 함께)에 참여한다. 외부 확인을 사이에 둔 충전 확정(ChargeConfirmationManager)과 같은 모양이다.
@Component
@RequiredArgsConstructor
public class ExecutionReservationManager {
    private final CostEstimateAssembler costEstimateAssembler;
    private final AbcBalanceReader abcBalanceReader;
    private final ExecutionWriter executionWriter;
    private final ExecutionReader executionReader;
    private final LedgerTransactionWriter ledgerTransactionWriter;
    private final AgentGradeMetricWriter agentGradeMetricWriter;
    private final ApplicationEventPublisher events;

    // 실행을 먼저 적어 같은 요청 번호가 동시에 오면 늦은 쪽이 막히게 하고, 잔액을 잠근 채 확인한 뒤 예치로 옮긴다.
    // 끝난 뒤 받을 단계마다의 단가도 이때 함께 적는다. 모자라면 예외가 나서 실행 기록까지 함께 되돌려진다.
    @Transactional
    public Execution reserve(Execution requested, Workflow workflow, ExecutionPlan plan) {
        CostEstimate estimate = costEstimateAssembler.assemble(workflow);
        Execution reserved = executionWriter.append(requested.reserve(estimate.total()));
        executionWriter.appendStepPrices(reserved.getId(), StepPrices.of(plan, estimate));

        CostQuote.of(estimate, abcBalanceReader.readForUpdate(reserved.getRequester())).ensureExecutable();
        ledgerTransactionWriter.append(LedgerTransaction.forExecutionReserve(
                reserved.getId().value(), reserved.getRequester(), reserved.getReserved()));

        return reserved;
    }

    // 브로커에 넘기지 못한 실행이다. 떼어 둔 금액을 돌려주고 실행은 플랫폼 탓 실패로 닫는다.
    // 행을 잠근 뒤에도 아직 넘긴 기록이 없고 닫히지 않았을 때만 닫는다. 그 사이 다른 쪽이 넘겼거나 닫았으면
    // 아무것도 하지 않는다 — 넘긴 실행을 돌려주면 엔진은 돌고 판매자는 못 받고, 닫힌 실행을 또 돌려주면 두 번 환불된다.
    @Transactional
    public boolean release(Execution reserved) {
        Optional<Execution> closed = executionWriter.finishUndispatched(
                reserved.getId(), ExecutionStatus.FAILED, ExecutionFailure.undispatched());

        if (closed.isEmpty()) {
            return false;
        }

        if (reserved.isReserved()) {
            ledgerTransactionWriter.append(LedgerTransaction.forExecutionRelease(
                    reserved.getId().value(), reserved.getRequester(), reserved.getReserved()));
        }

        events.publishEvent(new ExecutionClosed(reserved.getId(), EngineReport.undispatched()));

        return true;
    }

    // 브로커가 실행 맡기기를 받았다(confirm). runId 를 실행 번호로 적고 넘긴 시각을 남긴다. 행을 잠근 뒤 아직 넘기지 않았고
    // 끝나지 않은 실행에만 적고 true 다. 이미 넘겼거나(confirm 과 이벤트 · 주기 점검이 겹침) 끝났으면(버려진 실행) 아무것도 하지 않는다.
    // runId 는 실행 번호다 — 런타임은 SubmitRun 의 execution_id 를 run_id 로 쓴다 (ai/docs/integration-guide.md §4.3).
    @Transactional
    public boolean markDispatched(ExecutionId id, Instant dispatchedAt) {
        if (!executionWriter.markDispatched(id, RunId.of(id.value().toString()))) {
            return false;
        }

        executionWriter.markOutboxDispatched(id, dispatchedAt);

        return true;
    }

    // 실행을 닫는 자리는 여기 하나다. 완료 이벤트도, 감시가 엔진에 물어 닫을 때도, dead letter 로 닫을 때도 여기로 온다.
    // 닫기 · 책임 판정 · 등급 재료 · 환불 · 정산이 한 트랜잭션이라, 닫혔는데 돌려주지 않았거나 공급자에게 주지 않아
    // 예치에 남은 실행, 누구 탓인지 모르는 실행, 등급에 세지 않은 실행이 생기지 않는다.
    // 이미 닫힌 실행이면 아무것도 하지 않아, 같은 끝이 두 번 와도 한 번만 세고 한 번만 돌려주고 한 번만 적립한다.
    // run 은 엔진이 알린 마지막 상태다. 기한 안에 끝을 알리지 않아 우리가 끊은 실행은 비어 있다.
    // run 이 비어 있으면 기한 안에 끝을 듣지 못해 우리가 끊은 실행이다. 엔진 원본 없이 닫는 옛 입구다.
    // 아래 메서드를 자기 자신에게서 부르면 프록시를 거치지 않아 트랜잭션이 열리지 않으므로, 여기서도 연다.
    @Transactional
    public void finish(ExecutionId id, ExecutionStatus status, RunStatus run) {
        finish(id, status, run == null ? EngineReport.stalled(null, null) : EngineReport.reported(run, null));
    }

    // 여기서는 돈의 기록(닫기 · 실패 판정 · 환불 · 정산)만 한다. 정산 근거(단계 표 · 엔진 원본)는 이 트랜잭션이 커밋된 뒤
    // 따로 적는다(ExecutionEvidenceRecorder). 근거를 적다 느려지거나 실패해도 실행 닫기는 막히지 않는다.
    @Transactional
    public void finish(ExecutionId id, ExecutionStatus status, EngineReport report) {
        ExecutionFailure failure = status == ExecutionStatus.FAILED ? ExecutionFailure.judge(report) : null;
        Optional<Execution> closed = executionWriter.finish(id, status, failure);

        if (closed.isEmpty()) {
            return;
        }

        Execution execution = closed.get();
        RunStatus run = report.run();

        // mock 실행은 모델을 부르지 않아 성공도 실패도 판매자의 것이 아니다. 등급 재료로 세지 않는다.
        if (!execution.isMock()) {
            AgentRunOutcomes outcomes = AgentRunOutcomes.of(run, failure, execution.getRequester());
            agentGradeMetricWriter.countRuns(outcomes.succeeded(), outcomes.failed());
        }

        if (execution.isReserved()) {
            ExecutionUsage usage = ExecutionUsage.of(
                    execution.getReserved(), run, failure, executionReader.readStepPrices(id), execution.isMock());

            if (usage.hasRefund()) {
                ledgerTransactionWriter.append(LedgerTransaction.forExecutionRefund(
                        id.value(), execution.getRequester(), AbcAmount.of(usage.refund())));
            }

            if (usage.hasSettlement()) {
                executionWriter.appendSettlements(id, usage.settlements());
                ledgerTransactionWriter.append(settlementOf(id, execution, usage));
            }
        }

        events.publishEvent(new ExecutionClosed(id, report));
    }

    // 정산금은 보통 사용자의 예치에서 나가고, 플랫폼 탓으로 실패한 실행은 플랫폼이 대신 낸다.
    private static LedgerTransaction settlementOf(ExecutionId id, Execution execution, ExecutionUsage usage) {
        if (usage.coveredByPlatform()) {
            return LedgerTransaction.forPlatformCoveredSettlement(id.value(), usage.payouts());
        }

        return LedgerTransaction.forExecutionSettlement(id.value(), execution.getRequester(), usage.payouts());
    }
}
