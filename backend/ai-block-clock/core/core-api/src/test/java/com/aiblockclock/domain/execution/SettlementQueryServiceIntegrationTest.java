package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.DatePeriod;
import com.aiblockclock.domain.payment.AbcBalance;
import com.aiblockclock.domain.payment.AbcLedgerQueryService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.SettlementPeriodUnit;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 실행을 닫는 실제 경로(선차감 → 종료)로 정산을 만든 뒤, 같은 순간에 잔액 · 내역 · 통계가 모두 그것을 보는지 확인한다.
@Tag("integration")
@SpringBootTest
@Transactional
class SettlementQueryServiceIntegrationTest {
    private static final UserId BUYER = UserId.of(9801L);
    private static final UserId SELLER = UserId.of(9802L);
    private static final UserId OTHER_SELLER = UserId.of(9803L);

    @Autowired
    private SettlementQueryService settlementQueryService;

    @Autowired
    private AbcLedgerQueryService abcLedgerQueryService;

    @Autowired
    private ExecutionReservationManager executionReservationManager;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    private Workflow workflow;

    private ExecutionPlan plan;

    // 노드 두 개라 견적은 3 + 3 = 6 ABC 다.
    @BeforeEach
    void 견적이_6인_워크플로우와_충전한_잔액을_준비한다() {
        workflow = workflowService.save(BUYER, "정산 볼 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, BUYER.value())),
                        GraphFixtures.placement("n2", NodeDefinitionSeeder.seed(nodeRepository, BUYER.value()))),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        plan = executionPlanAssembler.assemble(workflow);

        LedgerAccountEntity account = ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, Long.toString(BUYER.value())));
        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal("10"), Instant.now());
    }

    @Test
    void 정산_직후_잔액과_내역과_통계가_모두_새_정산을_본다() {
        Execution reserved = executionReservationManager.reserve(requested(), workflow, plan);
        RunStatus run = run(soldStep("n1", "1.5", SELLER), soldStep("n2", "0.25", OTHER_SELLER));

        executionReservationManager.finish(reserved.getId(), ExecutionProgress.toStatus(run.state()), run);

        AbcBalance balance = abcLedgerQueryService.readBalance(SELLER);
        CursorPage<SettlementRecord> history = settlementQueryService.readHistory(
                SELLER, new SettlementSearchCondition(DatePeriod.allDates(), null), null, 20);
        SettlementStatistics statistics = settlementQueryService.readStatistics(
                SELLER, new SettlementSearchCondition(DatePeriod.allDates(), null), SettlementPeriodUnit.DAY);

        assertThat(balance.payout()).isEqualTo("1.500000");
        assertThat(history.content())
                .extracting(record -> record.executionId(), record -> record.settlement().stepId(),
                        record -> record.settlement().amount().value().stripTrailingZeros(), SettlementRecord::selfUse)
                .containsExactly(tuple(reserved.getId(), "n1", new BigDecimal("1.5"), false));
        assertThat(statistics.totalAmount()).isEqualByComparingTo(balance.payout());
        assertThat(statistics.executionCount()).isEqualTo(1);
    }

    private Execution requested() {
        return Execution.requested(ExecutionId.newId(), ExecutionRequestId.of(UUID.randomUUID()),
                workflow.getId(), BUYER, 2, null);
    }

    private static RunStatus run(RunStatus.Node... steps) {
        return new RunStatus("run-1", "succeeded", null, List.of(steps), List.of(), List.of(), null, null, false, null);
    }

    private static RunStatus.Node soldStep(String id, String costAbc, UserId seller) {
        return new RunStatus.Node(id, "노드 " + id, "llm", "succeeded", null, Long.toString(seller.value()),
                new BigDecimal(costAbc), null);
    }
}
