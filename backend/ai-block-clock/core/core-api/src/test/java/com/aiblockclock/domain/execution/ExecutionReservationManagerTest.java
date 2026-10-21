package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentGradeMetricWriter;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.payment.AbcBalance;
import com.aiblockclock.domain.payment.AbcBalanceReader;
import com.aiblockclock.domain.payment.LedgerTransaction;
import com.aiblockclock.domain.payment.LedgerTransactionWriter;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

// 넘긴 기록(B7 · B11)과 넘기지 못한 실행 버리기(B10), 접수 때 떼기(B1 · B3)와 닫을 때 돌려주기 · 정산(B14 ~ B17 · B30)이다.
// 행을 잠그고 보는 판단은 ExecutionWriter 가 하고, 여기서는 그 답에 따라 무엇을 더 적는지 본다.
// DB 위의 동작은 ExecutionReservationManagerIntegrationTest 가 본다.
class ExecutionReservationManagerTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final UserId REQUESTER = UserId.of(7001L);
    private static final UserId SELLER = UserId.of(8001L);
    private static final UserId OTHER_SELLER = UserId.of(8002L);
    private static final AgentId SUCCEEDED_AGENT = AgentId.of("3f0b1c2d-0000-4000-8000-0000000000a1");
    private static final AgentId FAILED_AGENT = AgentId.of("3f0b1c2d-0000-4000-8000-0000000000a2");
    private static final AbcAmount RESERVED = AbcAmount.of(new BigDecimal("6"));

    private final CostEstimateAssembler costEstimateAssembler = mock(CostEstimateAssembler.class);
    private final AbcBalanceReader abcBalanceReader = mock(AbcBalanceReader.class);
    private final ExecutionWriter executionWriter = mock(ExecutionWriter.class);
    private final ExecutionReader executionReader = mock(ExecutionReader.class);
    private final LedgerTransactionWriter ledgerTransactionWriter = mock(LedgerTransactionWriter.class);
    private final AgentGradeMetricWriter agentGradeMetricWriter = mock(AgentGradeMetricWriter.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

    private final ExecutionReservationManager sut = new ExecutionReservationManager(costEstimateAssembler,
            abcBalanceReader, executionWriter, executionReader, ledgerTransactionWriter, agentGradeMetricWriter,
            events);

    private final ExecutionId id = ExecutionId.newId();

    @Test
    void 처음_넘긴_실행이면_실행_번호를_runId_로_적고_넘긴_시각을_남긴다() {
        given(executionWriter.markDispatched(id, RunId.of(id.value().toString()))).willReturn(true);

        assertThat(sut.markDispatched(id, NOW)).isTrue();

        then(executionWriter).should().markOutboxDispatched(id, NOW);
    }

    // confirm 과 이벤트 · 주기 점검이 겹쳤거나, 그 사이 버려진 실행이다. 넘긴 시각도 다시 적지 않는다.
    @Test
    void 이미_넘겼거나_끝난_실행이면_넘긴_시각을_적지_않고_넘기지_않았다고_답한다() {
        given(executionWriter.markDispatched(id, RunId.of(id.value().toString()))).willReturn(false);

        assertThat(sut.markDispatched(id, NOW)).isFalse();

        then(executionWriter).should(never()).markOutboxDispatched(any(), any());
    }

    @Test
    void 넘기지_못한_실행을_닫으면_떼어_둔_금액을_돌려주고_닫힌_것을_알린다() {
        Execution reserved = execution(RESERVED);
        given(executionWriter.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched()))
                .willReturn(Optional.of(reserved));

        assertThat(sut.release(reserved)).isTrue();

        then(ledgerTransactionWriter).should()
                .append(LedgerTransaction.forExecutionRelease(id.value(), REQUESTER, RESERVED));
        then(events).should().publishEvent(new ExecutionClosed(id, EngineReport.undispatched()));
    }

    // 선차감이 생기기 전에 접수한 실행은 뗀 것이 없다. 돌려줄 것 없이 닫기만 한다.
    @Test
    void 뗀_것이_없는_실행을_닫으면_돌려주지_않고_닫힌_것만_알린다() {
        Execution unreserved = execution(null);
        given(executionWriter.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched()))
                .willReturn(Optional.of(unreserved));

        assertThat(sut.release(unreserved)).isTrue();

        then(ledgerTransactionWriter).shouldHaveNoInteractions();
        then(events).should().publishEvent(new ExecutionClosed(id, EngineReport.undispatched()));
    }

    // 행을 잠근 사이 다른 쪽이 넘겼거나 닫았다. 넘긴 실행을 돌려주거나 닫힌 실행을 또 돌려주면 안 된다.
    @Test
    void 그_사이_넘겼거나_닫힌_실행이면_돌려주지도_알리지도_않는다() {
        Execution reserved = execution(RESERVED);
        given(executionWriter.finishUndispatched(id, ExecutionStatus.FAILED, ExecutionFailure.undispatched()))
                .willReturn(Optional.empty());

        assertThat(sut.release(reserved)).isFalse();

        then(ledgerTransactionWriter).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
    }

    // B14 — 성공한 실행에는 책임 판정이 없다.
    @Test
    void 성공으로_닫으면_실패_판정_없이_닫는다() {
        RunStatus run = RunEventFixtures.succeeded(id.value().toString(), 1, 3, Long.toString(SELLER.value()))
                .snapshot();
        given(executionWriter.finish(id, ExecutionStatus.SUCCEEDED, null))
                .willReturn(Optional.of(closed(ExecutionStatus.SUCCEEDED, RESERVED, false)));
        given(executionReader.readStepPrices(id)).willReturn(prices("n1", "n2"));

        sut.finish(id, ExecutionStatus.SUCCEEDED, EngineReport.reported(run, null));

        then(executionWriter).should().finish(id, ExecutionStatus.SUCCEEDED, null);
    }

    // B19 · B33 — 이벤트 · 감시 · dead letter 가 겹쳐도 돈과 등급 재료는 한 번만 잡힌다.
    @Test
    void 이미_닫힌_실행이면_돈도_등급도_건드리지_않고_닫혔다고_알리지도_않는다() {
        given(executionWriter.finish(id, ExecutionStatus.SUCCEEDED, null)).willReturn(Optional.empty());

        sut.finish(id, ExecutionStatus.SUCCEEDED, EngineReport.reported(twoAgentsOneFailed(), null));

        then(ledgerTransactionWriter).shouldHaveNoInteractions();
        then(agentGradeMetricWriter).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
        then(executionWriter).should(never()).appendSettlements(any(), any());
    }

    // 에이전트마다 성공 한 번이나 공급자 탓 실패 한 번이다(AgentRunOutcomes).
    @Test
    void mock_이_아닌_실행을_닫으면_에이전트마다_성공과_실패를_등급_재료로_센다() {
        EngineReport report = EngineReport.reported(twoAgentsOneFailed(), null);
        givenClosed(report, closed(ExecutionStatus.FAILED, RESERVED, false));

        sut.finish(id, ExecutionStatus.FAILED, report);

        then(agentGradeMetricWriter).should().countRuns(Set.of(SUCCEEDED_AGENT), Set.of(FAILED_AGENT));
    }

    // mock 실행은 모델을 부르지 않아 성공도 실패도 판매자의 것이 아니다.
    @Test
    void mock_실행은_등급_재료로_세지_않는다() {
        EngineReport report = EngineReport.reported(twoAgentsOneFailed(), null);
        givenClosed(report, closed(ExecutionStatus.FAILED, RESERVED, true));

        sut.finish(id, ExecutionStatus.FAILED, report);

        then(agentGradeMetricWriter).shouldHaveNoInteractions();
    }

    // B14 · B15 — 성공한 단계의 단가(2)만 받고 나머지(4)는 돌려준다. 공급자 탓이라 사용자의 예치에서 정산한다.
    @Test
    void 예치한_실행이_공급자_탓으로_실패하면_성공한_단계만큼_정산하고_나머지를_돌려준다() {
        EngineReport report = EngineReport.reported(twoAgentsOneFailed(), null);
        givenClosed(report, closed(ExecutionStatus.FAILED, RESERVED, false));

        sut.finish(id, ExecutionStatus.FAILED, report);

        then(ledgerTransactionWriter).should().append(LedgerTransaction.forExecutionRefund(id.value(), REQUESTER,
                AbcAmount.of(new BigDecimal("4"))));
        then(executionWriter).should().appendSettlements(id, List.of(new ExecutionSettlement("a1.n1", "성적 분석",
                SELLER, SUCCEEDED_AGENT, AbcAmount.of(new BigDecimal("2")))));
        then(ledgerTransactionWriter).should().append(LedgerTransaction.forExecutionSettlement(id.value(), REQUESTER,
                Map.of(SELLER, AbcAmount.of(new BigDecimal("2")))));
        then(events).should().publishEvent(new ExecutionClosed(id, report));
    }

    // 선차감이 생기기 전에 접수한 실행이다. 돌려주거나 나눠 줄 예치가 없다.
    @Test
    void 떼어_둔_것이_없는_실행은_돌려주거나_정산하지_않고_닫혔다고만_알린다() {
        EngineReport report = EngineReport.reported(twoAgentsOneFailed(), null);
        givenClosed(report, closed(ExecutionStatus.FAILED, null, false));

        sut.finish(id, ExecutionStatus.FAILED, report);

        then(ledgerTransactionWriter).shouldHaveNoInteractions();
        then(executionWriter).should(never()).appendSettlements(any(), any());
        then(events).should().publishEvent(new ExecutionClosed(id, report));
    }

    // B30 — dead letter 는 플랫폼 탓이라 사용자는 전부 돌려받고, 앞서 성공한 단계의 몫(2)은 플랫폼이 낸다.
    @Test
    void dead_letter_로_닫으면_전부_돌려주고_앞서_성공한_단계의_몫은_플랫폼이_낸다() {
        RunStatus lastSeen = RunEventFixtures.progress(id.value().toString(), 1, 2, Long.toString(SELLER.value()))
                .snapshot();
        EngineReport report = EngineReport.deadLettered(lastSeen, ExecutionProgress.QUEUE_EXPIRED);
        given(executionWriter.finish(id, ExecutionStatus.FAILED,
                new ExecutionFailure("n2", ExecutionProgress.QUEUE_EXPIRED, ExecutionFault.PLATFORM, false, null, null)))
                .willReturn(Optional.of(closed(ExecutionStatus.FAILED, RESERVED, false)));
        given(executionReader.readStepPrices(id)).willReturn(prices("n1", "n2"));

        sut.finish(id, ExecutionStatus.FAILED, report);

        then(ledgerTransactionWriter).should().append(LedgerTransaction.forExecutionRefund(id.value(), REQUESTER,
                RESERVED));
        then(ledgerTransactionWriter).should().append(LedgerTransaction.forPlatformCoveredSettlement(id.value(),
                Map.of(SELLER, AbcAmount.of(new BigDecimal("2")))));
        then(ledgerTransactionWriter).should(never()).append(LedgerTransaction.forExecutionSettlement(id.value(),
                REQUESTER, Map.of(SELLER, AbcAmount.of(new BigDecimal("2")))));
        then(events).should().publishEvent(new ExecutionClosed(id, report));
    }

    // B1 — 견적만큼 예치로 떼어 둔 실행을 적고, 끝난 뒤 받을 단계마다의 단가를 남긴다.
    @Test
    void 접수하면_견적만큼_떼어_둔_실행을_적고_단계마다_단가를_남기고_예치로_옮긴다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.straightLine());
        ExecutionPlan plan = singleStepPlan();
        CostEstimate estimate = estimateOf("5");
        Execution stored = execution(AbcAmount.of(new BigDecimal("5")));
        given(costEstimateAssembler.assemble(workflow)).willReturn(estimate);
        given(executionWriter.append(any())).willReturn(stored);
        given(abcBalanceReader.readForUpdate(REQUESTER)).willReturn(balance("10"));

        Execution reserved = sut.reserve(requested(), workflow, plan);

        assertThat(reserved).isSameAs(stored);
        ArgumentCaptor<Execution> appended = ArgumentCaptor.forClass(Execution.class);
        then(executionWriter).should().append(appended.capture());
        assertThat(appended.getValue().getId()).isEqualTo(id);
        assertThat(appended.getValue().getReserved()).isEqualTo(AbcAmount.of(new BigDecimal("5")));
        then(executionWriter).should().appendStepPrices(id, StepPrices.of(plan, estimate));
        then(ledgerTransactionWriter).should().append(LedgerTransaction.forExecutionReserve(id.value(), REQUESTER,
                AbcAmount.of(new BigDecimal("5"))));
    }

    // B3 — 모자라면 예외가 나서 실행 기록까지 함께 되돌려진다. 예치로 옮기는 기록은 남기지 않는다.
    @Test
    void 잔액이_모자라면_ABC_BALANCE_INSUFFICIENT_이고_예치로_옮기지_않는다() {
        Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.straightLine());
        given(costEstimateAssembler.assemble(workflow)).willReturn(estimateOf("5"));
        given(executionWriter.append(any())).willReturn(execution(AbcAmount.of(new BigDecimal("5"))));
        given(abcBalanceReader.readForUpdate(REQUESTER)).willReturn(balance("1"));

        assertThatThrownBy(() -> sut.reserve(requested(), workflow, singleStepPlan()))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ABC_BALANCE_INSUFFICIENT));

        then(ledgerTransactionWriter).shouldHaveNoInteractions();
    }

    private void givenClosed(EngineReport report, Execution closed) {
        given(executionWriter.finish(id, ExecutionStatus.FAILED, ExecutionFailure.judge(report)))
                .willReturn(Optional.of(closed));
        given(executionReader.readStepPrices(id)).willReturn(prices("a1.n1", "a2.n1"));
    }

    // 단계 둘의 단가가 2 와 3 이다. 떼어 둔 6 보다 작다.
    private static StepPrices prices(String first, String second) {
        return new StepPrices(Map.of(
                first, new StepPrice(BigDecimal.ONE, AbcAmount.of(new BigDecimal("2"))),
                second, new StepPrice(BigDecimal.ONE, AbcAmount.of(new BigDecimal("3")))));
    }

    // 에이전트 둘이 한 단계씩 돌았다. 앞 에이전트(판매자 8001)는 성공하고, 뒤 에이전트(판매자 8002)가 공급자 탓으로 실패했다.
    private static RunStatus twoAgentsOneFailed() {
        return MAPPER.readValue("""
                {"run_id":"r1","status":"failed",
                 "progress":{"done":1,"total":2,"percent":50,"current_node_id":"a2.n1","current_node_name":"가정통신문 작성"},
                 "nodes":[{"id":"a1.n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s",
                           "agent_id":"%s","cost_usd":0.004},
                          {"id":"a2.n1","name":"가정통신문 작성","type":"llm","status":"failed","owner_id":"%s",
                           "agent_id":"%s","cost_usd":0.001}],
                 "failure":{"node_id":"a2.n1","error_code":"MODEL_TIMEOUT","fault":"supplier","affects_success_rate":true}}"""
                .formatted(SELLER.value(), SUCCEEDED_AGENT.value(), OTHER_SELLER.value(), FAILED_AGENT.value()),
                RunStatus.class);
    }

    private static ExecutionPlan singleStepPlan() {
        return ExecutionPlanner.plan(new UnitGraph(List.of(new PlacedUnit(NodeKey.of("n1"), new NodeUnit(
                InputSource.PREV, SELLER, "성적 분석", Prompt.of("점수를 비교한다"), AiModel.defaultModel(),
                new NodeOutput(OutputKind.TEXT, null), List.of(), AbcAmount.of(new BigDecimal("5"))))), List.of()));
    }

    private static CostEstimate estimateOf(String total) {
        return CostEstimate.of(List.of(new CostItem(NodeKey.of("n1"), SourceType.NODE, "성적 분석",
                AbcAmount.of(new BigDecimal(total)), BigDecimal.ONE)));
    }

    private static AbcBalance balance(String available) {
        return AbcBalance.of(new BigDecimal(available), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private Execution requested() {
        return Execution.requested(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                REQUESTER, 1, null, false);
    }

    private Execution closed(ExecutionStatus status, AbcAmount reserved, boolean mock) {
        return Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                REQUESTER, RunId.of(id.value().toString()), status, 2, null, mock, reserved);
    }

    private Execution execution(AbcAmount reserved) {
        return Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                REQUESTER, null, ExecutionStatus.QUEUED, 2, null, false, reserved);
    }
}
