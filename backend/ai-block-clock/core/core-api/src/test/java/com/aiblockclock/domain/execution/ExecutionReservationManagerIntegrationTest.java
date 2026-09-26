package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentGradeMetricReader;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryEntity;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionEntity;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

// 되돌림과 잠금을 보려면 실제로 커밋 · 롤백되어야 해서 테스트 트랜잭션을 쓰지 않고, 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:execution_reservation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class ExecutionReservationManagerIntegrationTest {
    private static final UserId OWNER = UserId.of(9601L);
    private static final String OWNER_REFERENCE_ID = Long.toString(OWNER.value());
    private static final UserId SELLER = UserId.of(9602L);
    private static final UserId OTHER_SELLER = UserId.of(9603L);

    @Autowired
    private ExecutionReservationManager executionReservationManager;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Autowired
    private ExecutionChargeRateRepository executionChargeRateRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

    @Autowired
    private ExecutionFailureRepository executionFailureRepository;

    @Autowired
    private AgentGradeMetricReader agentGradeMetricReader;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    private Workflow workflow;

    private ExecutionPlan plan;

    // 노드 두 개라 견적은 3 + 3 = 6 ABC 다.
    @BeforeEach
    void 견적이_6인_워크플로우를_저장해_둔다() {
        workflow = workflowService.save(OWNER, "선차감 볼 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value())),
                        GraphFixtures.placement("n2", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()))),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        plan = executionPlanAssembler.assemble(workflow);
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        executionChargeRateRepository.deleteAllInBatch();
        executionSettlementRepository.deleteAllInBatch();
        executionFailureRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        agentGradeMetricRepository.deleteAllInBatch();
    }

    @Test
    void 잔액이_넉넉하면_실행을_적고_견적만큼_예치로_옮긴다() {
        charge("10");
        Execution requested = requested(newRequestId());

        Execution reserved = executionReservationManager.reserve(requested, workflow, plan);

        assertThat(reserved.getReserved().value()).isEqualByComparingTo("6");
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
        assertThat(executionRepository.findById(requested.getId().value()).orElseThrow())
                .satisfies(execution -> {
                    assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
                    assertThat(execution.getRunId()).isNull();
                });
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-reserve:" + requested.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.RESERVE);
        assertBalancesMatchEntries();
    }

    @Test
    void 잔액이_모자라면_실행도_장부도_남기지_않고_부족액을_알린다() {
        charge("4");
        Execution requested = requested(newRequestId());

        assertThatThrownBy(() -> executionReservationManager.reserve(requested, workflow, plan))
                .isInstanceOfSatisfying(CoreException.class, exception -> {
                    assertThat(exception.getErrorType()).isEqualTo(ErrorType.ABC_BALANCE_INSUFFICIENT);
                    assertThat(exception.getData()).isEqualTo(Map.of(
                            "estimatedAmount", "6.000000",
                            "availableAmount", "4.000000",
                            "shortageAmount", "2.000000"));
                });

        assertThat(executionRepository.count()).isZero();
        assertThat(ledgerTransactionRepository.count()).isZero();
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
    }

    @Test
    void 충전한_적이_없으면_견적_전액이_모자라다고_막는다() {
        Execution requested = requested(newRequestId());

        assertThatThrownBy(() -> executionReservationManager.reserve(requested, workflow, plan))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.ABC_BALANCE_INSUFFICIENT));

        assertThat(executionRepository.count()).isZero();
    }

    @Test
    void 떼어_둔_금액을_되돌리면_잔액이_원래대로_돌아오고_실행은_실패로_남는다() {
        charge("10");
        Execution requested = requested(newRequestId());
        Execution reserved = executionReservationManager.reserve(requested, workflow, plan);

        executionReservationManager.release(reserved);

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(statusOf(requested)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failureOf(requested))
                .satisfies(failure -> {
                    assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
                    assertThat(failure.getFailureCode()).isEqualTo(ExecutionFailure.UNREACHABLE);
                });
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-release:" + requested.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.REFUND);
        assertBalancesMatchEntries();
    }

    @Test
    void 되돌리기를_두_번_해도_한_번만_돌려준다() {
        charge("10");
        Execution requested = requested(newRequestId());
        Execution reserved = executionReservationManager.reserve(requested, workflow, plan);

        executionReservationManager.release(reserved);
        executionReservationManager.release(reserved);

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_요청_번호로_다시_접수하면_막히고_한_번만_뗀다() {
        charge("20");
        ExecutionRequestId requestId = newRequestId();
        executionReservationManager.reserve(requested(requestId), workflow, plan);

        assertThatThrownBy(() -> executionReservationManager.reserve(requested(requestId), workflow, plan))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_ALREADY_REQUESTED));

        assertThat(executionRepository.count()).isEqualTo(1);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("14");
    }

    @Test
    void 동시에_들어온_실행은_잔액을_넘겨_떼지_못한다() throws Exception {
        charge("10");

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> executionReservationManager.reserve(requested(newRequestId()), workflow, plan),
                () -> executionReservationManager.reserve(requested(newRequestId()), workflow, plan)));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.DONE, Outcome.INSUFFICIENT);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
        assertThat(executionRepository.count()).isEqualTo(1);
        assertBalancesMatchEntries();
    }

    @Test
    void 여러_실행이_한꺼번에_들어와도_잔액이_음수가_되지_않는다() throws Exception {
        charge("30");
        List<Callable<?>> reservations = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            reservations.add(() -> executionReservationManager.reserve(requested(newRequestId()), workflow, plan));
        }

        List<Outcome> outcomes = runConcurrently(reservations);

        assertThat(outcomes).filteredOn(Outcome.DONE::equals).hasSize(5);
        assertThat(outcomes).filteredOn(Outcome.INSUFFICIENT::equals).hasSize(3);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("30");
        assertBalancesMatchEntries();
    }

    @Test
    void 같은_요청_번호가_동시에_들어와도_한_번만_뗀다() throws Exception {
        charge("20");
        ExecutionRequestId requestId = newRequestId();

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> executionReservationManager.reserve(requested(requestId), workflow, plan),
                () -> executionReservationManager.reserve(requested(requestId), workflow, plan)));

        assertThat(outcomes).containsExactlyInAnyOrder(Outcome.DONE, Outcome.ALREADY_REQUESTED);
        assertThat(executionRepository.count()).isEqualTo(1);
        assertThat(ledgerTransactionRepository.count()).isEqualTo(1);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("14");
    }

    @Test
    void 접수할_때_단계마다_받을_단가와_그_배율을_남긴다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        assertThat(executionChargeRateRepository.findAllByExecutionId(reserved.getId().value()))
                .extracting(ExecutionChargeRateEntity::getStepId, rate -> rate.getPriceMultiplier().stripTrailingZeros(),
                        rate -> rate.getUnitPrice().stripTrailingZeros())
                .containsExactlyInAnyOrder(
                        tuple("n1", BigDecimal.ONE, new BigDecimal("3")),
                        tuple("n2", BigDecimal.ONE, new BigDecimal("3")));
    }

    // 떼어 둔 6 은 두 단계의 단가 3 + 3 이다. 모두 성공하면 전부 쓰고 돌려줄 것이 없다.
    @Test
    void 모두_성공하면_단계마다_정해_둔_단가만큼_쓰고_돌려줄_것이_없어_환불을_적지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("succeeded", null,
                step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.25")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("6");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(executionFailureRepository.findById(reserved.getId().value())).isEmpty();
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-refund:" + reserved.getId().value()))
                .isEmpty();
        assertBalancesMatchEntries();
    }

    // 성공한 n1 의 단가 3 만 쓰고 실패한 n2 의 단가 3 은 돌려준다.
    @Test
    void 실패한_단계와_돌지_않은_단계의_단가는_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-refund:" + reserved.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.REFUND);
        assertBalancesMatchEntries();
    }

    @Test
    void 플랫폼_탓으로_실패하면_사용자에게는_성공한_단계까지_전부_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("platform"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertBalancesMatchEntries();
    }

    @Test
    void 엔진이_잰_원가가_단가보다_커도_단가만큼만_받는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("supplier"),
                step("n1", "succeeded", "5"), step("n2", "failed", "4")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
        assertBalancesMatchEntries();
    }

    // 접수 · 환불 · 정산 세 거래가 한 번씩만 남는다.
    @Test
    void 같은_끝을_두_번_봐도_한_번만_돌려주고_한_번만_적립한다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        RunStatus finished = run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.5"));

        finish(reserved, finished);
        finish(reserved, finished);

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(3);
        assertThat(executionSettlementRepository.count()).isEqualTo(1);
    }

    @Test
    void 폴링이_겹쳐_같은_끝을_동시에_봐도_한_번만_돌려주고_한_번만_적립한다() throws Exception {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        RunStatus finished = run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.5"));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> {
                    finish(reserved, finished);

                    return null;
                },
                () -> {
                    finish(reserved, finished);

                    return null;
                }));

        assertThat(outcomes).containsOnly(Outcome.DONE);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(3);
        assertThat(executionSettlementRepository.count()).isEqualTo(1);
        assertBalancesMatchEntries();
    }

    @Test
    void 엔진이_기한_안에_끝을_알리지_않아_실패로_끊으면_전부_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        executionReservationManager.finish(reserved.getId(), ExecutionStatus.FAILED, null);

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failureOf(reserved))
                .satisfies(failure -> {
                    assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
                    assertThat(failure.getFailureCode()).isEqualTo(ExecutionProgress.STALLED);
                });
        assertBalancesMatchEntries();
    }

    @Test
    void 공급자_탓으로_실패하면_누구_탓인지와_실패한_단계의_주인_에이전트를_남긴다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        UUID agent = UUID.randomUUID();

        finish(reserved, run("failed", new RunStatus.Failure("n2", "MODEL_TIMEOUT", "supplier", true),
                step("n1", "succeeded", "0.5"), ownedStep("n2", "failed", "9602", agent)));

        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failureOf(reserved))
                .satisfies(failure -> {
                    assertThat(failure.getFailedStepId()).isEqualTo("n2");
                    assertThat(failure.getFailureCode()).isEqualTo("MODEL_TIMEOUT");
                    assertThat(failure.getFault()).isEqualTo(ExecutionFault.SUPPLIER);
                    assertThat(failure.isAffectsSuccessRate()).isTrue();
                    assertThat(failure.getSupplierId()).isEqualTo(9602L);
                    assertThat(failure.getAgentId()).isEqualTo(agent);
                });
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
    }

    @Test
    void 수요자_탓으로_실패하면_공급자를_남기지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", new RunStatus.Failure("n1", "FILE_PARSE_FAILED", "consumer", false),
                ownedStep("n1", "failed", "9602", UUID.randomUUID()), step("n2", "pending", "0")));

        assertThat(failureOf(reserved))
                .satisfies(failure -> {
                    assertThat(failure.getFault()).isEqualTo(ExecutionFault.CONSUMER);
                    assertThat(failure.isAffectsSuccessRate()).isFalse();
                    assertThat(failure.getSupplierId()).isNull();
                    assertThat(failure.getAgentId()).isNull();
                });
    }

    @Test
    void 이미_닫힌_실행을_다시_봐도_처음_판정을_바꾸지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0")));
        finish(reserved, run("failed", failure("platform"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0")));

        assertThat(failureOf(reserved).getFault()).isEqualTo(ExecutionFault.SUPPLIER);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
    }

    @Test
    void 엔진이_칸보다_긴_값을_보내도_잘라_넣고_실행을_닫아_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        String longStep = "a1.".repeat(10) + "n2";
        String longCode = "MODEL_API_ERROR_".repeat(10);

        finish(reserved, run("failed", new RunStatus.Failure(longStep, longCode, "supplier", true),
                step("n1", "succeeded", "0.5")));

        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(failureOf(reserved))
                .satisfies(failure -> {
                    assertThat(failure.getFailedStepId()).isEqualTo(longStep.substring(0, 16));
                    assertThat(failure.getFailureCode()).isEqualTo(longCode.substring(0, 64));
                });
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
    }

    @Test
    void 같은_실행의_판정을_두_번_적으면_DB_가_막는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        finish(reserved, run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0")));

        assertThatThrownBy(() -> executionFailureRepository.saveAndFlush(new ExecutionFailureEntity(
                reserved.getId().value(), "n1", "RATE_LIMITED", ExecutionFault.PLATFORM, false, null, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(failureOf(reserved).getFault()).isEqualTo(ExecutionFault.SUPPLIER);
    }

    @Test
    void 선차감_전에_접수한_실행은_끝나도_돌려줄_것이_없다() {
        Execution requested = requested(newRequestId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                executionRepository.save(ExecutionEntityConverter.toEntity(requested)));

        finish(requested, run("succeeded", null, step("n1", "succeeded", "0.5")));

        assertThat(statusOf(requested)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    // 떼어 둔 6 을 두 판매자에게 단가 3 씩 준다. 엔진이 잰 원가(1.5 · 0.25)와 상관없다. 예치에는 아무것도 남지 않는다.
    @Test
    void 끝나면_쓴_만큼을_단계_주인의_정산금으로_옮기고_예치를_비운다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("succeeded", null,
                soldStep("n1", "succeeded", "1.5", SELLER), soldStep("n2", "succeeded", "0.25", OTHER_SELLER)));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("3");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, OTHER_SELLER)).isEqualByComparingTo("3");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isZero();
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-settlement:" + reserved.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.SETTLEMENT);
        assertBalancesMatchEntries();
    }

    @Test
    void 정산할_때_단계마다_누구에게_얼마를_주었는지_남긴다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("succeeded", null,
                soldStep("n1", "succeeded", "1.5", SELLER), soldStep("n2", "succeeded", "0.25", OTHER_SELLER)));

        assertThat(executionSettlementRepository.findAll())
                .extracting(ExecutionSettlementEntity::getExecutionId, ExecutionSettlementEntity::getStepId,
                        ExecutionSettlementEntity::getNodeName, ExecutionSettlementEntity::getSupplierId,
                        settlement -> settlement.getAmountAbc().stripTrailingZeros())
                .containsExactlyInAnyOrder(
                        tuple(reserved.getId().value(), "n1", "노드 n1", SELLER.value(), new BigDecimal("3")),
                        tuple(reserved.getId().value(), "n2", "노드 n2", OTHER_SELLER.value(), new BigDecimal("3")));
        assertThat(executionSettlementRepository.findAll())
                .allSatisfy(settlement -> assertThat(settlement.getCreatedAt()).isNotNull());
    }

    @Test
    void 엔진이_칸보다_긴_노드_이름을_돌려줘도_잘라_넣고_실행을_닫아_정산한다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        String longName = "가정통신문 작성 ".repeat(10);

        finish(reserved, run("failed", failure("supplier"),
                new RunStatus.Node("n1", longName, "llm", "succeeded", null, Long.toString(SELLER.value()),
                        new BigDecimal("0.5"), null),
                step("n2", "failed", "0.1")));

        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertThat(executionSettlementRepository.findAll())
                .singleElement()
                .satisfies(settlement -> assertThat(settlement.getNodeName()).isEqualTo(longName.substring(0, 40)));
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("3");
        assertBalancesMatchEntries();
    }

    // 우리는 단계를 n1 · n2 로 매겨 보내고 그 번호로 단가를 남긴다. 엔진이 보내지 않은 번호를 돌려주면 받을 값이 없다.
    @Test
    void 엔진이_보내지_않은_단계_번호를_돌려주면_단가가_없어_받지_않고_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        String longStep = "a1.".repeat(10) + "n1";

        finish(reserved, run("succeeded", null, new RunStatus.Node(longStep, "노드", "llm", "succeeded", null,
                Long.toString(SELLER.value()), new BigDecimal("0.5"), null)));

        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(executionSettlementRepository.count()).isZero();
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertBalancesMatchEntries();
    }

    @Test
    void 떼어_둔_것은_돌려준_것과_정산한_것의_합과_같다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("supplier"),
                soldStep("n1", "succeeded", "0.7", SELLER), soldStep("n2", "failed", "0.3", OTHER_SELLER)));

        BigDecimal refunded = balanceOf(LedgerAccountType.ABC_BALANCE).subtract(new BigDecimal("4"));
        BigDecimal settled = balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)
                .add(balanceOf(LedgerAccountType.PAYOUT_BALANCE, OTHER_SELLER));

        assertThat(refunded.add(settled)).isEqualByComparingTo("6");
        assertThat(settled).isEqualByComparingTo("3");
        assertBalancesMatchEntries();
    }

    // 사용자는 떼어 둔 6 을 모두 돌려받고, 앞서 성공한 n1 의 판매자는 단가 3 을 받는다. 그 3 은 플랫폼 부담 계정이 낸다.
    @Test
    void 플랫폼_탓으로_실패해도_앞서_성공한_판매자는_제_단가를_플랫폼에게서_받는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("platform"),
                soldStep("n1", "succeeded", "0.5", SELLER), soldStep("n2", "failed", "0.3", OTHER_SELLER)));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("3");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, OTHER_SELLER)).isZero();
        assertThat(platformExpense()).isEqualByComparingTo("-3");
        assertThat(executionSettlementRepository.findAll())
                .extracting(ExecutionSettlementEntity::getStepId, ExecutionSettlementEntity::getSupplierId)
                .containsExactly(tuple("n1", SELLER.value()));
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-settlement:" + reserved.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.SETTLEMENT);
        assertBalancesMatchEntries();
    }

    @Test
    void 자기_에이전트를_자기가_쓰면_쓴_만큼이_자기_정산금이_된다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("succeeded", null, step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.5")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("6");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
    }

    @Test
    void 남의_에이전트가_끝까지_돌면_닫을_때_그_에이전트의_성공_수를_하나_늘린다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        AgentId agent = AgentId.newId();

        finish(reserved, run("succeeded", null,
                agentStep("n1", "succeeded", "0.5", SELLER, agent), agentStep("n2", "succeeded", "0.5", SELLER, agent)));

        assertThat(agentGradeMetricReader.read(agent))
                .satisfies(metric -> {
                    assertThat(metric.successfulRuns()).isEqualTo(1);
                    assertThat(metric.supplierFailures()).isZero();
                    assertThat(metric.measuredAt()).isNotNull();
                });
    }

    @Test
    void 공급자_탓으로_실패하면_그_에이전트의_실패_수를_늘리고_앞서_끝난_에이전트는_성공_수를_늘린다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        AgentId finished = AgentId.newId();
        AgentId blamed = AgentId.newId();

        finish(reserved, run("failed", new RunStatus.Failure("n2", "MODEL_TIMEOUT", "supplier", true),
                agentStep("n1", "succeeded", "0.5", SELLER, finished),
                agentStep("n2", "failed", "0.1", OTHER_SELLER, blamed)));

        assertThat(agentGradeMetricReader.read(finished).successfulRuns()).isEqualTo(1);
        assertThat(agentGradeMetricReader.read(blamed))
                .satisfies(metric -> {
                    assertThat(metric.successfulRuns()).isZero();
                    assertThat(metric.supplierFailures()).isEqualTo(1);
                });
    }

    @Test
    void 플랫폼_탓으로_실패하면_등급_재료를_남기지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("failed", failure("platform"),
                agentStep("n1", "succeeded", "0.5", SELLER, AgentId.newId()),
                agentStep("n2", "failed", "0.1", OTHER_SELLER, AgentId.newId())));

        assertThat(agentGradeMetricRepository.count()).isZero();
    }

    @Test
    void 자기_에이전트를_자기가_쓰면_등급_재료를_남기지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, run("succeeded", null,
                agentStep("n1", "succeeded", "0.5", OWNER, AgentId.newId()),
                agentStep("n2", "succeeded", "0.5", OWNER, AgentId.newId())));

        assertThat(agentGradeMetricRepository.count()).isZero();
    }

    @Test
    void 같은_끝을_두_번_봐도_등급_재료는_한_번만_센다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        AgentId agent = AgentId.newId();
        RunStatus finished = run("succeeded", null,
                agentStep("n1", "succeeded", "0.5", SELLER, agent), agentStep("n2", "succeeded", "0.5", SELLER, agent));

        finish(reserved, finished);
        finish(reserved, finished);

        assertThat(agentGradeMetricReader.read(agent).successfulRuns()).isEqualTo(1);
    }

    @Test
    void 선차감_전에_접수한_실행도_끝나면_등급_재료는_센다() {
        Execution requested = requested(newRequestId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                executionRepository.save(ExecutionEntityConverter.toEntity(requested)));
        AgentId agent = AgentId.newId();

        finish(requested, run("succeeded", null, agentStep("n1", "succeeded", "0.5", SELLER, agent)));

        assertThat(agentGradeMetricReader.read(agent).successfulRuns()).isEqualTo(1);
        assertThat(ledgerTransactionRepository.count()).isZero();
    }

    // 둘 다 처음 세는 에이전트라 줄을 동시에 만들려고 한다. 한 줄만 생기고 두 번 모두 더해져야 한다.
    @Test
    void 두_실행이_같은_에이전트를_동시에_닫아도_하나를_잃지_않고_둘_다_센다() throws Exception {
        charge("20");
        Execution first = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        Execution second = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        AgentId agent = AgentId.newId();
        RunStatus finished = run("succeeded", null,
                agentStep("n1", "succeeded", "0.5", SELLER, agent), agentStep("n2", "succeeded", "0.5", SELLER, agent));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> {
                    finish(first, finished);

                    return null;
                },
                () -> {
                    finish(second, finished);

                    return null;
                }));

        assertThat(outcomes).containsOnly(Outcome.DONE);
        assertThat(agentGradeMetricRepository.count()).isEqualTo(1);
        assertThat(agentGradeMetricReader.read(agent).successfulRuns()).isEqualTo(2);
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("12");
    }

    @Test
    void mock_실행은_모델을_부르지_않아_전부_돌려주고_정산을_적지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        finish(reserved, new RunStatus("run-1", "succeeded", null,
                List.of(step("n1", "succeeded", "0"), step("n2", "succeeded", "0")), List.of(), List.of(), null, null,
                true, null));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(executionSettlementRepository.count()).isZero();
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-settlement:" + reserved.getId().value()))
                .isEmpty();
    }

    // 엔진이 mock 칸을 빠뜨려도 접수할 때 적은 값으로 판정한다. 원가가 잰 값처럼 와도 받지 않는다.
    @Test
    void 접수할_때_mock_으로_적은_실행은_엔진이_알리지_않아도_전부_돌려주고_정산하지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(mockRequested(), workflow, plan);

        finish(reserved, run("succeeded", null,
                soldStep("n1", "succeeded", "0.004", SELLER), soldStep("n2", "succeeded", "0.004", SELLER)));

        assertThat(entityOf(reserved).isMock()).isTrue();
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isZero();
        assertThat(executionSettlementRepository.count()).isZero();
        assertBalancesMatchEntries();
    }

    // mock 은 모델을 부르지 않아 성공도 실패도 판매자의 것이 아니다. 공급자 탓 실패로 세면 등급이 억울하게 내려간다.
    @Test
    void mock_실행은_성공도_공급자_탓_실패도_등급_재료로_세지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(mockRequested(), workflow, plan);

        finish(reserved, run("failed", new RunStatus.Failure("n2", "MODEL_TIMEOUT", "supplier", true),
                agentStep("n1", "succeeded", "0.004", SELLER, AgentId.newId()),
                agentStep("n2", "failed", "0.001", OTHER_SELLER, AgentId.newId())));

        assertThat(agentGradeMetricRepository.count()).isZero();
    }

    private Execution mockRequested() {
        return Execution.requested(ExecutionId.newId(), newRequestId(), workflow.getId(), OWNER, 2, null, true);
    }

    private enum Outcome { DONE, INSUFFICIENT, ALREADY_REQUESTED }

    private List<Outcome> runConcurrently(List<Callable<?>> tasks) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Future<?>> futures = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();

        try {
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);

                    return task.call();
                }));
            }

            for (Future<?> future : futures) {
                outcomes.add(outcomeOf(future));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        return outcomes;
    }

    private static Outcome outcomeOf(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);

            return Outcome.DONE;
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof CoreException failure)) {
                throw exception;
            }

            return switch (failure.getErrorType()) {
                case ABC_BALANCE_INSUFFICIENT -> Outcome.INSUFFICIENT;
                case EXECUTION_ALREADY_REQUESTED -> Outcome.ALREADY_REQUESTED;
                default -> throw exception;
            };
        }
    }

    private Execution requested(ExecutionRequestId requestId) {
        return Execution.requested(ExecutionId.newId(), requestId, workflow.getId(), OWNER, 2, null, false);
    }

    private static ExecutionRequestId newRequestId() {
        return ExecutionRequestId.of(UUID.randomUUID());
    }

    private void finish(Execution execution, RunStatus run) {
        executionReservationManager.finish(execution.getId(), ExecutionProgress.toStatus(run.state()), run);
    }

    private ExecutionStatus statusOf(Execution execution) {
        return entityOf(execution).getStatus();
    }

    private ExecutionEntity entityOf(Execution execution) {
        return executionRepository.findById(execution.getId().value()).orElseThrow();
    }

    private ExecutionFailureEntity failureOf(Execution execution) {
        return executionFailureRepository.findById(execution.getId().value()).orElseThrow();
    }

    private static RunStatus run(String state, RunStatus.Failure failure, RunStatus.Node... steps) {
        return new RunStatus("run-1", state, null, List.of(steps), List.of(), List.of(), null, failure, false, null);
    }

    // 워크플로우의 노드는 모두 OWNER 가 만든 것이라, 따로 적지 않은 단계는 OWNER 의 것이다 (자기 것을 자기가 쓰는 실행).
    private static RunStatus.Node step(String id, String state, String costUsd) {
        return new RunStatus.Node(id, id, "llm", state, null, OWNER_REFERENCE_ID, new BigDecimal(costUsd), null);
    }

    private static RunStatus.Node soldStep(String id, String state, String costUsd, UserId seller) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, null, Long.toString(seller.value()),
                new BigDecimal(costUsd), null);
    }

    // 엔진은 우리가 보낸 주인과 에이전트를 단계마다 그대로 돌려준다.
    private static RunStatus.Node ownedStep(String id, String state, String owner, UUID agent) {
        return new RunStatus.Node(id, id, "llm", state, agent.toString(), owner, BigDecimal.ZERO, null);
    }

    // 판매자의 에이전트 안에서 돈 단계다. 실측 원가가 있어 모델을 실제로 부른 것이다.
    private static RunStatus.Node agentStep(String id, String state, String costUsd, UserId seller, AgentId agent) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, agent.value().toString(),
                Long.toString(seller.value()), new BigDecimal(costUsd), null);
    }

    private static RunStatus.Failure failure(String fault) {
        return new RunStatus.Failure("n2", "MODEL_API_ERROR", fault, true);
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void charge(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, OWNER_REFERENCE_ID));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now()));
    }

    private BigDecimal balanceOf(LedgerAccountType accountType) {
        return balanceOf(accountType, OWNER);
    }

    private BigDecimal platformExpense() {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(LedgerAccountType.PLATFORM_EXPENSE, "PLATFORM")
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private BigDecimal balanceOf(LedgerAccountType accountType, UserId user) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(user.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    // 충전은 장부 항목 없이 넣었으므로 예치와 정산금 계정만 항목 합과 맞춰 본다.
    private void assertBalancesMatchEntries() {
        List<LedgerAccountEntity> accounts = ledgerAccountRepository.findAll().stream()
                .filter(account -> account.getAccountType() != LedgerAccountType.ABC_BALANCE)
                .toList();

        assertThat(accounts).isNotEmpty();

        for (LedgerAccountEntity account : accounts) {
            BigDecimal entryTotal = ledgerEntryRepository.findAllByAccountIdOrderByIdAsc(account.getId()).stream()
                    .map(LedgerEntryEntity::getAmountDelta)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(account.getBalance()).isEqualByComparingTo(entryTotal);
        }
    }
}
