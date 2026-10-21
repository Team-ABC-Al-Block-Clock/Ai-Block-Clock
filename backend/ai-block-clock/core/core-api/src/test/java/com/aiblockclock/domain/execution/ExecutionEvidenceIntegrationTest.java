package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionRunRecordEntity;
import com.aiblockclock.storage.execution.ExecutionRunRecordRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.execution.ExecutionStepEntity;
import com.aiblockclock.storage.execution.ExecutionStepRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.tuple;

// 실행이 닫힌 뒤 근거(단계 표 · 엔진 원본)가 따로 적히는지, 빠진 근거를 백필이 채우는지,
// 관리자가 그것을 한자리에서 꺼내 보는지 본다. 근거는 닫는 트랜잭션 밖에서 근거 스레드가 적으므로 적힐 때까지 기다린다.
// 선차감 통합 테스트와 같은 컨텍스트 · DB 를 쓴다 — 컨텍스트를 하나 더 띄우면 테스트 JVM 힙이 모자란다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:execution_reservation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class ExecutionEvidenceIntegrationTest {
    private static final UserId OWNER = UserId.of(9701L);
    private static final String OWNER_REFERENCE_ID = Long.toString(OWNER.value());
    private static final UserId SELLER = UserId.of(9702L);

    @Autowired
    private ExecutionReservationManager executionReservationManager;

    @Autowired
    private ExecutionEvidenceService executionEvidenceService;

    @Autowired
    private ExecutionWriter executionWriter;

    @Autowired
    private ExecutionEvidenceRecorder executionEvidenceRecorder;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionStepRepository executionStepRepository;

    @Autowired
    private ExecutionRunRecordRepository executionRunRecordRepository;

    @Autowired
    private ExecutionChargeRateRepository executionChargeRateRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

    @Autowired
    private ExecutionFailureRepository executionFailureRepository;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Workflow workflow;

    private ExecutionPlan plan;

    // 노드 두 개라 견적은 3 + 3 = 6 ABC 다.
    @BeforeEach
    void 견적이_6인_워크플로우를_저장해_둔다() {
        workflow = workflowService.save(OWNER, "근거 볼 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value())),
                        GraphFixtures.placement("n2", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()))),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        plan = executionPlanAssembler.assemble(workflow);
        charge("10");
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        executionStepRepository.deleteAllInBatch();
        executionRunRecordRepository.deleteAllInBatch();
        executionChargeRateRepository.deleteAllInBatch();
        executionSettlementRepository.deleteAllInBatch();
        executionFailureRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        agentGradeMetricRepository.deleteAllInBatch();
    }

    @Test
    void 판매자_탓으로_실패하면_돈_안_받은_단계까지_엔진이_알린_사실과_단가를_단계마다_남긴다() {
        Execution reserved = reserve();
        RunStatus run = new RunStatus("run-1", "failed", null, List.of(
                step("n1", "succeeded", 1200, 0, "2026-10-15T01:00:00+00:00"),
                step("n2", "failed", 900, 2, "2026-10-15T01:00:20+00:00")),
                List.of(), List.of(), null,
                new RunStatus.Failure("n2", "SCHEMA_VALIDATION_FAILED", "supplier", true), false, null);

        executionReservationManager.finish(reserved.getId(), ExecutionStatus.FAILED,
                EngineReport.reported(run, "{\"engine\":\"raw\"}"));
        recordOf(reserved);

        assertThat(executionStepRepository.findAllByExecutionIdOrderByIdAsc(reserved.getId().value()))
                .extracting(ExecutionStepEntity::getStepId, ExecutionStepEntity::getStatus,
                        ExecutionStepEntity::getInputTokens, ExecutionStepEntity::getRepairCount,
                        step -> step.getUnitPrice().stripTrailingZeros(),
                        step -> step.getSettledAbc() == null ? null : step.getSettledAbc().stripTrailingZeros())
                .containsExactly(
                        tuple("n1", "succeeded", 1200, 0, new BigDecimal("3"), new BigDecimal("3")),
                        tuple("n2", "failed", 900, 2, new BigDecimal("3"), null));
        assertThat(recordOf(reserved)).satisfies(record -> {
            assertThat(record.getClosedBy()).isEqualTo("ENGINE");
            assertThat(record.getEngineStatus()).isEqualTo("failed");
            assertThat(record.getResponse()).isEqualTo("{\"engine\":\"raw\"}");
            assertThat(record.getResponseSha256()).isEqualTo(ExecutionEvidenceWriter.sha256("{\"engine\":\"raw\"}"));
        });
    }

    // 사용자는 6 을 모두 돌려받고, 끊기 전에 이미 성공한 n1 의 판매자는 단가 3 을 플랫폼에게서 받는다 (계약 8.5).
    // 예전에는 끊은 실행의 마지막 상태를 버려 n1 이 성공했는지 몰랐고, 판매자는 아무것도 받지 못했다.
    @Test
    void 기한_초과로_끊어도_마지막으로_본_상태로_성공한_단계를_알고_그_판매자는_플랫폼에게서_받는다() {
        Execution reserved = reserve();
        RunStatus lastSeen = new RunStatus("run-1", "running",
                new RunStatus.Progress(1, 2, 50, "n2", "노드 n2"),
                List.of(step("n1", "succeeded", 1000, 0, null), step("n2", "running", null, null, null)),
                List.of(), List.of(), null, null, false, null);

        executionReservationManager.finish(reserved.getId(), ExecutionStatus.FAILED,
                EngineReport.stalled(lastSeen, null));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("3");
        assertThat(executionFailureRepository.findById(reserved.getId().value())).get().satisfies(failure -> {
            assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
            assertThat(failure.getFailureCode()).isEqualTo(ExecutionProgress.STALLED);
            assertThat(failure.getFailedStepId()).isEqualTo("n2");
        });
        assertThat(recordOf(reserved)).satisfies(record -> {
            assertThat(record.getClosedBy()).isEqualTo("STALLED");
            assertThat(record.getResponse()).isNull();
            assertThat(record.getNote()).contains("엔진 원본을 받지 못했다");
        });
    }

    @Test
    void 엔진이_실행을_잊어_닫으면_LOST_로_남기고_전부_돌려준다() {
        Execution reserved = reserve();

        executionReservationManager.finish(reserved.getId(), ExecutionStatus.FAILED, EngineReport.lost(null));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
        assertThat(executionFailureRepository.findById(reserved.getId().value())).get()
                .extracting(failure -> failure.getFailureCode()).isEqualTo(ExecutionProgress.LOST);
        assertThat(recordOf(reserved).getClosedBy()).isEqualTo("LOST");
    }

    @Test
    void 엔진_원본이_너무_크면_원본은_빼고_지문만_남긴_채_실행을_닫는다() {
        Execution reserved = reserve();
        String huge = "x".repeat(ExecutionEvidenceWriter.MAX_RESPONSE_CHARS + 1);

        executionReservationManager.finish(reserved.getId(), ExecutionStatus.SUCCEEDED, EngineReport.reported(
                new RunStatus("run-1", "succeeded", null, List.of(), List.of(), List.of(), null, null, false, null),
                huge));

        assertThat(executionRepository.findById(reserved.getId().value())).get()
                .extracting(entity -> entity.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(recordOf(reserved)).satisfies(record -> {
            assertThat(record.getResponse()).isNull();
            assertThat(record.getResponseSha256()).isEqualTo(ExecutionEvidenceWriter.sha256(huge));
            assertThat(record.getNote()).contains("지문만");
        });
    }

    @Test
    void 엔진에_넘기지_못해_돌려주면_UNDISPATCHED_로_남긴다() {
        Execution reserved = reserve();

        assertThat(executionReservationManager.release(reserved)).isTrue();

        assertThat(recordOf(reserved).getClosedBy()).isEqualTo("UNDISPATCHED");
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
    }

    // 복구가 "넘기지 못한 실행" 으로 본 사이에 접수 요청이 넘겼다면, 돌려주면 엔진은 돌고 판매자는 못 받는다.
    @Test
    void 그_사이_엔진에_넘긴_실행은_돌려주지_않는다() {
        Execution reserved = reserve();
        executionWriter.markDispatched(reserved.getId(), RunId.of("run-1"));

        assertThat(executionReservationManager.release(reserved)).isFalse();

        assertThat(executionRepository.findById(reserved.getId().value())).get()
                .extracting(entity -> entity.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("4");
    }

    @Test
    void 관리자는_돈의_흐름과_근거를_한자리에서_꺼내_본다() {
        Execution reserved = reserve();
        RunStatus run = new RunStatus("run-1", "succeeded", null, List.of(
                step("n1", "succeeded", 1000, 0, null), step("n2", "succeeded", 800, 1, null)),
                List.of(), List.of(), null, null, false, null);
        executionReservationManager.finish(reserved.getId(), ExecutionStatus.SUCCEEDED,
                EngineReport.reported(run, "{\"status\":\"succeeded\"}"));
        recordOf(reserved);

        ExecutionEvidence evidence = executionEvidenceService.read(reserved.getId());

        assertThat(evidence.execution().status()).isEqualTo("SUCCEEDED");
        assertThat(evidence.chargeRates()).hasSize(2);
        assertThat(evidence.steps()).extracting(ExecutionEvidence.Step::stepId).containsExactly("n1", "n2");
        assertThat(evidence.settlements()).hasSize(2);
        assertThat(evidence.ledger()).extracting(ExecutionEvidence.LedgerTransaction::eventType)
                .containsExactly("RESERVE", "SETTLEMENT");
        assertThat(evidence.runRecord().response()).isEqualTo("{\"status\":\"succeeded\"}");
    }

    // 근거 스레드가 적기 전에 서버가 내려갔거나 적다 실패한 경우다. 닫기 · 환불은 이미 끝나 있고, 근거만 비어 있다.
    @Test
    void 근거가_빠진_채_닫힌_실행은_백필이_채운다() {
        Execution reserved = reserve();
        executionWriter.markDispatched(reserved.getId(), RunId.of("run-1"));
        executionReservationManager.finish(reserved.getId(), ExecutionStatus.FAILED, EngineReport.lost(null));
        recordOf(reserved);
        executionRunRecordRepository.deleteById(reserved.getId().value());

        assertThat(executionEvidenceRecorder.backfill(10)).isEqualTo(1);

        assertThat(recordOf(reserved).getClosedBy()).isEqualTo("LOST");
        assertThat(executionEvidenceRecorder.backfill(10)).isZero();
    }

    private Execution reserve() {
        return executionReservationManager.reserve(Execution.requested(ExecutionId.newId(),
                ExecutionRequestId.of(UUID.randomUUID()), workflow.getId(), OWNER, 2, null, false), workflow, plan);
    }

    // 근거는 닫기가 커밋된 뒤 근거 스레드가 적는다. 엔진에 원본을 물으러 가므로(테스트에는 엔진이 없어 실패한다) 넉넉히 기다린다.
    private ExecutionRunRecordEntity recordOf(Execution execution) {
        return await().atMost(Duration.ofSeconds(30))
                .until(() -> executionRunRecordRepository.findById(execution.getId().value()), java.util.Optional::isPresent)
                .orElseThrow();
    }

    private static RunStatus.Node step(String id, String state, Integer inputTokens, Integer repairCount,
                                       String startedAt) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, null, Long.toString(SELLER.value()),
                new BigDecimal("0.0021"), null, "gemini-2.5-pro", inputTokens, 300, 0, repairCount, startedAt,
                null, 15000L, null);
    }

    private void charge(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, OWNER_REFERENCE_ID));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now()));
    }

    private BigDecimal balanceOf(LedgerAccountType accountType, UserId user) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(user.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }
}
