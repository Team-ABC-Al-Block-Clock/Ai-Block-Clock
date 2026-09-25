package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.payment.LedgerEventType;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
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
        executionRepository.deleteAllInBatch();
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
        assertThat(executionRepository.findById(requested.getId().value()).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.FAILED);
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
    void 접수할_때_단계마다_실측_원가에_곱할_배율을_남긴다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        assertThat(executionChargeRateRepository.findAllByExecutionId(reserved.getId().value()))
                .extracting(ExecutionChargeRateEntity::getStepId, rate -> rate.getPriceMultiplier().stripTrailingZeros())
                .containsExactlyInAnyOrder(tuple("n1", BigDecimal.ONE), tuple("n2", BigDecimal.ONE));
    }

    // 떼어 둔 6 중 성공한 두 단계의 실측 0.5 + 0.25 만 쓰고 5.25 를 돌려준다.
    @Test
    void 끝나면_성공한_단계의_실측_원가만큼만_쓰고_나머지를_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        executionReservationManager.finish(reserved, run("succeeded", null,
                step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.25")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("9.25");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0.75");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-refund:" + reserved.getId().value()))
                .get().extracting(LedgerTransactionEntity::getEventType).isEqualTo(LedgerEventType.REFUND);
        assertBalancesMatchEntries();
    }

    @Test
    void 실패한_단계와_돌지_않은_단계는_사용액에_넣지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        executionReservationManager.finish(reserved, run("failed", failure("supplier"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("9.5");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0.5");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.FAILED);
        assertBalancesMatchEntries();
    }

    @Test
    void 플랫폼_탓으로_실패하면_성공한_단계까지_전부_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        executionReservationManager.finish(reserved, run("failed", failure("platform"),
                step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertBalancesMatchEntries();
    }

    @Test
    void 실측이_떼어_둔_것을_넘으면_돌려줄_것이_없어_환불을_적지_않는다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);

        executionReservationManager.finish(reserved, run("succeeded", null,
                step("n1", "succeeded", "5"), step("n2", "succeeded", "4")));

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
        assertThat(statusOf(reserved)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(ledgerTransactionRepository.findByIdempotencyKey("execution-refund:" + reserved.getId().value()))
                .isEmpty();
    }

    @Test
    void 같은_끝을_두_번_봐도_한_번만_돌려준다() {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        RunStatus finished = run("succeeded", null, step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.5"));

        executionReservationManager.finish(reserved, finished);
        executionReservationManager.finish(reserved, finished);

        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("9");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(2);
    }

    @Test
    void 폴링이_겹쳐_같은_끝을_동시에_봐도_한_번만_돌려준다() throws Exception {
        charge("10");
        Execution reserved = executionReservationManager.reserve(requested(newRequestId()), workflow, plan);
        RunStatus finished = run("succeeded", null, step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.5"));

        List<Outcome> outcomes = runConcurrently(List.of(
                () -> {
                    executionReservationManager.finish(reserved, finished);

                    return null;
                },
                () -> {
                    executionReservationManager.finish(reserved, finished);

                    return null;
                }));

        assertThat(outcomes).containsOnly(Outcome.DONE);
        assertThat(balanceOf(LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("9");
        assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("1");
        assertThat(ledgerTransactionRepository.count()).isEqualTo(2);
        assertBalancesMatchEntries();
    }

    @Test
    void 선차감_전에_접수한_실행은_끝나도_돌려줄_것이_없다() {
        Execution requested = requested(newRequestId());
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                executionRepository.save(ExecutionEntityConverter.toEntity(requested)));

        executionReservationManager.finish(requested, run("succeeded", null, step("n1", "succeeded", "0.5")));

        assertThat(statusOf(requested)).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(ledgerTransactionRepository.count()).isZero();
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
        return Execution.requested(ExecutionId.newId(), requestId, workflow.getId(), OWNER, 2, null);
    }

    private static ExecutionRequestId newRequestId() {
        return ExecutionRequestId.of(UUID.randomUUID());
    }

    private ExecutionStatus statusOf(Execution execution) {
        return executionRepository.findById(execution.getId().value()).orElseThrow().getStatus();
    }

    private static RunStatus run(String state, RunStatus.Failure failure, RunStatus.Node... steps) {
        return new RunStatus("run-1", state, null, List.of(steps), List.of(), List.of(), null, failure);
    }

    private static RunStatus.Node step(String id, String state, String costAbc) {
        return new RunStatus.Node(id, id, "llm", state, null, null, new BigDecimal(costAbc), null);
    }

    private static RunStatus.Failure failure(String fault) {
        return new RunStatus.Failure("n2", "MODEL_API_ERROR", fault);
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void charge(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, OWNER_REFERENCE_ID));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now()));
    }

    private BigDecimal balanceOf(LedgerAccountType accountType) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, OWNER_REFERENCE_ID)
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    // 충전은 장부 항목 없이 넣었으므로 예치 계정만 항목 합과 맞춰 본다.
    private void assertBalancesMatchEntries() {
        LedgerAccountEntity escrow = ledgerAccountRepository
                .findByAccountTypeAndReferenceId(LedgerAccountType.EXECUTION_ESCROW, OWNER_REFERENCE_ID)
                .orElseThrow();
        BigDecimal entryTotal = ledgerEntryRepository.findAllByAccountIdOrderByIdAsc(escrow.getId()).stream()
                .map(LedgerEntryEntity::getAmountDelta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(escrow.getBalance()).isEqualByComparingTo(entryTotal);
    }
}
