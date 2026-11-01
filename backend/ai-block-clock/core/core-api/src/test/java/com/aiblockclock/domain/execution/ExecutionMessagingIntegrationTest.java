package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionFailureRepository;
import com.aiblockclock.storage.execution.ExecutionOutboxEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionResultRepository;
import com.aiblockclock.storage.execution.ExecutionRunRecordEntity;
import com.aiblockclock.storage.execution.ExecutionRunRecordRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.execution.ExecutionStepRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;

// 접수(DB outbox) → 디스패처 발행 → 이벤트 적용 · dead letter 로 닫기를 실제 DB 위에서 본다(ADR-0001).
// 브로커와 엔진은 목으로 바꾼다. 커밋 뒤에 도는 것(발행 · 근거)을 봐야 해서 테스트 트랜잭션을 쓰지 않고 따로 떼어 낸 DB 에서 돈다.
// 단계 둘(n1 · n2)이고 단가는 3 + 3 = 6 ABC 다. 잔액은 10 으로 시작한다.
@Tag("integration")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:execution_messaging;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "ai.runtime.results-root=build/tmp/execution-messaging-results"})
class ExecutionMessagingIntegrationTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Path RESULTS_ROOT = Path.of("build/tmp/execution-messaging-results");
    private static final UserId OWNER = UserId.of(9801L);
    private static final String OWNER_REFERENCE_ID = Long.toString(OWNER.value());
    private static final UserId SELLER = UserId.of(9802L);
    private static final String ARTIFACT = RunEventFixtures.ARTIFACT;

    @MockitoBean
    private RunQueuePublisher runQueuePublisher;

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private ExecutionDispatcher executionDispatcher;

    @Autowired
    private ExecutionReservationManager executionReservationManager;

    @Autowired
    private ExecutionProgressStore progressStore;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionOutboxRepository executionOutboxRepository;

    @Autowired
    private ExecutionFailureRepository executionFailureRepository;

    @Autowired
    private ExecutionChargeRateRepository executionChargeRateRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

    @Autowired
    private ExecutionRunRecordRepository executionRunRecordRepository;

    @Autowired
    private ExecutionStepRepository executionStepRepository;

    @Autowired
    private ExecutionResultRepository executionResultRepository;

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

    @BeforeEach
    void 견적이_6인_워크플로우와_잔액_10을_둔다() {
        workflow = workflowService.save(OWNER, "브로커로 맡길 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value())),
                        GraphFixtures.placement("n2", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()))),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        charge("10");
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        executionStepRepository.deleteAllInBatch();
        executionRunRecordRepository.deleteAllInBatch();
        executionResultRepository.deleteAllInBatch();
        executionChargeRateRepository.deleteAllInBatch();
        executionSettlementRepository.deleteAllInBatch();
        executionFailureRepository.deleteAllInBatch();
        executionOutboxRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        agentGradeMetricRepository.deleteAllInBatch();
    }

    @Nested
    class 접수와_발행 {

        @Test
        void 접수하면_QUEUED_로_남고_runId_는_비어_있고_견적만큼_예치로_옮긴다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker down"))
                    .given(runQueuePublisher).submit(any());

            Execution started = start(UUID.randomUUID()).execution();

            assertThat(started.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(started.getRunId()).isNull();
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("4");
            assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW, OWNER)).isEqualByComparingTo("6");
        }

        @Test
        void 접수와_같은_트랜잭션에_그_실행의_SubmitRun_을_outbox_에_남긴다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker down"))
                    .given(runQueuePublisher).submit(any());

            ExecutionId id = start(UUID.randomUUID()).execution().getId();

            JsonNode payload = MAPPER.readTree(outboxOf(id).getPayload());
            assertThat(payload.get("execution_id").asString()).isEqualTo(id.value().toString());
            assertThat(payload.get("mode").asString()).isEqualTo("run");
            assertThat(payload.get("workflow").isObject()).isTrue();
            assertThat(payload.get("workflow").get("nodes")).hasSize(2);
            assertThat(payload.get("files")).isEmpty();
            assertThat(payload.get("ref_files")).isEmpty();
        }

        @Test
        void 잔액이_모자라면_실행도_outbox_도_예치도_남기지_않는다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker down"))
                    .given(runQueuePublisher).submit(any());
            start(UUID.randomUUID());
            UUID requestId = UUID.randomUUID();

            assertThatThrownBy(() -> start(requestId))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.ABC_BALANCE_INSUFFICIENT));

            assertThat(executionRepository.findByRequesterIdAndRequestId(OWNER.value(), requestId)).isEmpty();
            assertThat(executionOutboxRepository.count()).isEqualTo(1);
            assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW, OWNER)).isEqualByComparingTo("6");
        }

        @Test
        void 접수가_커밋된_뒤_요청_스레드가_아닌_곳에서_곧바로_발행하고_confirm_을_받으면_실행_번호를_runId_로_남긴다() {
            AtomicReference<Thread> publishedOn = new AtomicReference<>();
            willAnswer(invocation -> {
                publishedOn.set(Thread.currentThread());

                return null;
            }).given(runQueuePublisher).submit(any());

            ExecutionId id = start(UUID.randomUUID()).execution().getId();

            await().atMost(Duration.ofSeconds(10)).until(() -> runIdOf(id) != null);
            assertThat(runIdOf(id)).isEqualTo(id.value().toString());
            assertThat(outboxOf(id).getDispatchedAt()).isNotNull();
            assertThat(publishedOn.get()).isNotNull().isNotSameAs(Thread.currentThread());
        }

        @Test
        void 발행이_느려도_접수는_발행을_기다리지_않는다() {
            CountDownLatch brokerAnswers = new CountDownLatch(1);
            willAnswer(invocation -> brokerAnswers.await(10, TimeUnit.SECONDS)).given(runQueuePublisher).submit(any());

            try {
                assertTimeoutPreemptively(Duration.ofSeconds(3), () -> start(UUID.randomUUID()));
            } finally {
                brokerAnswers.countDown();
            }
        }

        @Test
        void 브로커가_죽어_있어도_접수는_되고_runId_는_비어_있다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "connection refused"))
                    .given(runQueuePublisher).submit(any());

            ExecutionId id = start(UUID.randomUUID()).execution().getId();

            then(runQueuePublisher).should(timeout(5_000)).submit(any());
            assertThat(runIdOf(id)).isNull();
            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.QUEUED);
        }

        @Test
        void 같은_요청_번호로_다시_오면_앞_실행을_돌려주고_다시_발행하지_않는다() {
            UUID requestId = UUID.randomUUID();
            ExecutionId first = start(requestId).execution().getId();
            then(runQueuePublisher).should(timeout(5_000)).submit(any());

            ExecutionStart again = start(requestId);

            assertThat(again.execution().getId()).isEqualTo(first);
            assertThat(again.created()).isFalse();
            then(runQueuePublisher).should(after(1_000).times(1)).submit(any());
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("4");
        }

        @Test
        void 주기_점검은_넘기지_않은_실행만_발행하고_넘긴_실행과_끝난_실행은_건너뛴다() {
            charge("20");
            ExecutionId dispatched = start(UUID.randomUUID()).execution().getId();
            await().atMost(Duration.ofSeconds(10)).until(() -> runIdOf(dispatched) != null);
            ExecutionId pending = undispatchedExecution();
            ExecutionId abandoned = undispatchedExecution();
            executionReservationManager.release(executionService.mine(abandoned, OWNER));
            clearInvocations(runQueuePublisher);

            executionDispatcher.dispatchPending();

            then(runQueuePublisher).should().submit(argThat(run -> run.executionId().equals(pending.value().toString())));
            then(runQueuePublisher).shouldHaveNoMoreInteractions();
            assertThat(runIdOf(pending)).isEqualTo(pending.value().toString());
            assertThat(runIdOf(abandoned)).isNull();
        }

        @Test
        void 같은_실행을_동시에_두_번_넘겨도_runId_는_한_번_적히고_오류가_나지_않는다() throws Exception {
            ExecutionId id = undispatchedExecution();
            ExecutorService threads = Executors.newFixedThreadPool(2);

            try {
                Future<Boolean> first = threads.submit(() -> executionDispatcher.dispatch(id));
                Future<Boolean> second = threads.submit(() -> executionDispatcher.dispatch(id));

                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                threads.shutdownNow();
            }

            assertThat(runIdOf(id)).isEqualTo(id.value().toString());
            assertThat(outboxOf(id).getDispatchedAt()).isNotNull();
        }

        // 디스패처가 발행하는 사이 한도가 지나 버려졌다. confirm 이 늦게 와도 끝난 실행을 넘긴 것으로 되돌리지 않는다.
        @Test
        void confirm_이_오기_전에_끝난_실행에는_runId_를_적지_않는다() {
            ExecutionId id = undispatchedExecution();
            willAnswer(invocation -> {
                executionReservationManager.release(executionService.mine(id, OWNER));

                return null;
            }).given(runQueuePublisher).submit(any());

            executionDispatcher.dispatch(id);

            assertThat(runIdOf(id)).isNull();
            assertThat(outboxOf(id).getDispatchedAt()).isNull();
            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.FAILED);
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
        }

        @Test
        void 버려진_실행은_한_건씩_넘기려_해도_발행하지_않는다() {
            ExecutionId id = undispatchedExecution();
            executionReservationManager.release(executionService.mine(id, OWNER));
            clearInvocations(runQueuePublisher);

            assertThat(executionDispatcher.dispatch(id)).isFalse();

            then(runQueuePublisher).should(never()).submit(any());
            assertThat(runIdOf(id)).isNull();
        }

        @Test
        void 이미_넘긴_실행은_한_건씩_넘기려_해도_다시_발행하지_않는다() {
            ExecutionId id = start(UUID.randomUUID()).execution().getId();
            await().atMost(Duration.ofSeconds(10)).until(() -> runIdOf(id) != null);
            clearInvocations(runQueuePublisher);

            assertThat(executionDispatcher.dispatch(id)).isFalse();

            then(runQueuePublisher).should(never()).submit(any());
        }

        @Test
        void 접수하고_발행하는_동안_엔진_HTTP_를_부르지_않는다() {
            start(UUID.randomUUID());

            then(runQueuePublisher).should(timeout(5_000)).submit(any());
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    class 이벤트로_닫는다 {

        @BeforeEach
        void 발행은_늘_confirm_을_받는다() {
            willDoNothing().given(runQueuePublisher).submit(any());
        }

        @Test
        void accepted_가_오면_DB_상태가_RUNNING_이_된다() {
            ExecutionId id = dispatchedExecution();

            executionService.applyEvent(RunEventFixtures.accepted(id.value().toString(), 1, 1));

            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.RUNNING);
        }

        @Test
        void succeeded_로_닫으면_단가만큼_정산하고_결과_볼륨의_결과물을_보관한다() throws IOException {
            ExecutionId id = dispatchedExecution();
            writeResult(id, "artifact.json", ARTIFACT);
            writeResult(id, "report.pdf", "%PDF-1.7");

            executionService.applyEvent(RunEventFixtures.accepted(id.value().toString(), 1, 1));
            executionService.applyEvent(RunEventFixtures.succeeded(id.value().toString(), 1, 2, OWNER_REFERENCE_ID));

            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("4");
            assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW, OWNER)).isEqualByComparingTo("0");
            assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, OWNER)).isEqualByComparingTo("6");
            assertThat(executionResultRepository.findByExecutionIdOrderByIdAsc(id.value()))
                    .extracting(result -> result.getFormat().name())
                    .containsExactlyInAnyOrder("JSON", "PDF");
            then(aiRuntimeClient).should(never()).readArtifact(anyString());
        }

        @Test
        void 완료_이벤트로_닫은_실행의_정산_근거는_snapshot_원문으로_적고_엔진에_묻지_않는다() {
            ExecutionId id = dispatchedExecution();
            RunEvent failed = RunEventFixtures.failed(id.value().toString(), 1, 2, OWNER_REFERENCE_ID);

            executionService.applyEvent(failed);

            ExecutionRunRecordEntity record = recordOf(id);
            assertThat(record.getClosedBy()).isEqualTo("ENGINE");
            assertThat(record.getResponse()).isEqualTo(failed.snapshotJson());
            then(aiRuntimeClient).should(never()).readRecord(anyString());
        }

        @Test
        void failed_로_닫으면_실패한_단계만_돌려주고_snapshot_의_failure_로_판정한다() {
            ExecutionId id = dispatchedExecution();

            executionService.applyEvent(RunEventFixtures.failed(id.value().toString(), 1, 2, OWNER_REFERENCE_ID));

            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.FAILED);
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("7");
            assertThat(executionFailureRepository.findById(id.value())).get().satisfies(failure -> {
                assertThat(failure.getFailureCode()).isEqualTo("MODEL_TIMEOUT");
                assertThat(failure.getFault()).isEqualTo(ExecutionFault.SUPPLIER);
                assertThat(failure.getFailedStepId()).isEqualTo("n2");
            });
        }

        @Test
        void 끝난_실행에_늦게_온_이벤트는_상태도_돈도_바꾸지_않는다() {
            ExecutionId id = dispatchedExecution();
            executionService.applyEvent(RunEventFixtures.succeeded(id.value().toString(), 1, 2, OWNER_REFERENCE_ID));

            executionService.applyEvent(RunEventFixtures.progress(id.value().toString(), 2, 1, OWNER_REFERENCE_ID));
            executionService.applyEvent(RunEventFixtures.failed(id.value().toString(), 2, 2, OWNER_REFERENCE_ID));

            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.SUCCEEDED);
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("4");
            assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, OWNER)).isEqualByComparingTo("6");
            assertThat(executionFailureRepository.findById(id.value())).isEmpty();
        }

        @Test
        void runId_가_적히기_전에_이벤트가_와도_실행_번호로_넘긴_기록을_남기고_적용한다() {
            ExecutionId id = undispatchedExecution();

            executionService.applyEvent(RunEventFixtures.accepted(id.value().toString(), 1, 1));

            assertThat(runIdOf(id)).isEqualTo(id.value().toString());
            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.RUNNING);
        }

        @Test
        void 모르는_실행의_이벤트는_던지지_않는다() {
            executionService.applyEvent(RunEventFixtures.progress(UUID.randomUUID().toString(), 1, 1, OWNER_REFERENCE_ID));
        }

        @Test
        void 큐에서_만료된_실행은_ABC_QUEUE_EXPIRED_플랫폼_탓으로_닫고_전부_돌려준다() {
            ExecutionId id = dispatchedExecution();

            executionService.closeDeadLettered(id, DeadLetterReason.EXPIRED);

            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.FAILED);
            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
            assertThat(balanceOf(LedgerAccountType.EXECUTION_ESCROW, OWNER)).isEqualByComparingTo("0");
            assertThat(executionFailureRepository.findById(id.value())).get().satisfies(failure -> {
                assertThat(failure.getFailureCode()).isEqualTo("ABC_QUEUE_EXPIRED");
                assertThat(failure.getFault()).isEqualTo(ExecutionFault.PLATFORM);
            });
        }

        // 다시 배달되기 전 n1 이 이미 성공했다. 사용자는 전부 돌려받고, n1 판매자의 단가 3 은 플랫폼이 낸다.
        @Test
        void 재배달_한도를_넘긴_실행은_전부_돌려주고_앞서_성공한_단계의_판매자는_플랫폼에게서_받는다() {
            ExecutionId id = dispatchedExecution();
            String seller = Long.toString(SELLER.value());
            executionService.applyEvent(RunEventFixtures.accepted(id.value().toString(), 1, 1));
            executionService.applyEvent(RunEventFixtures.progress(id.value().toString(), 1, 2, seller));

            executionService.closeDeadLettered(id, DeadLetterReason.DELIVERY_LIMIT);

            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
            assertThat(balanceOf(LedgerAccountType.PAYOUT_BALANCE, SELLER)).isEqualByComparingTo("3");
            assertThat(executionFailureRepository.findById(id.value())).get()
                    .extracting(failure -> failure.getFailureCode()).isEqualTo("ABC_RUNTIME_RETRY_EXHAUSTED");
        }

        @Test
        void 같은_dead_letter_가_두_번_와도_한_번만_돌려준다() {
            ExecutionId id = dispatchedExecution();

            executionService.closeDeadLettered(id, DeadLetterReason.EXPIRED);
            executionService.closeDeadLettered(id, DeadLetterReason.OTHER);

            assertThat(balanceOf(LedgerAccountType.ABC_BALANCE, OWNER)).isEqualByComparingTo("10");
            assertThat(executionFailureRepository.findById(id.value())).get()
                    .extracting(failure -> failure.getFailureCode()).isEqualTo("ABC_QUEUE_EXPIRED");
        }

        @Test
        void dead_letter_로_닫은_실행의_근거는_엔진에_원본을_묻지_않고_DEAD_LETTERED_로_적는다() {
            ExecutionId id = dispatchedExecution();

            executionService.closeDeadLettered(id, DeadLetterReason.DELIVERY_LIMIT);

            assertThat(recordOf(id).getClosedBy()).isEqualTo("DEAD_LETTERED");
            then(aiRuntimeClient).should(never()).readRecord(anyString());
        }

        // 서버를 다시 띄워 진행 저장소가 비었다. 이벤트가 다시 오거나 감시가 닫을 때까지 DB 상태로 답한다.
        @Test
        void 진행_저장소에_없는_RUNNING_실행의_조회는_DB_상태로_답하고_엔진을_부르지_않고_닫지도_않는다() {
            ExecutionId id = dispatchedExecution();
            executionService.applyEvent(RunEventFixtures.progress(id.value().toString(), 1, 1, OWNER_REFERENCE_ID));
            progressStore.forget(id);

            ExecutionProgress progress = executionService.follow(id, OWNER);

            assertThat(progress.status()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(progress.total()).isEqualTo(2);
            assertThat(progress.steps()).isEmpty();
            assertThat(statusOf(id)).isEqualTo(ExecutionStatus.RUNNING);
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 진행_저장소에_없는_QUEUED_실행의_조회는_대기_상태로_답하고_엔진을_부르지_않는다() {
            ExecutionId id = dispatchedExecution();
            progressStore.forget(id);

            ExecutionProgress progress = executionService.follow(id, OWNER);

            assertThat(progress.status()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(progress.total()).isEqualTo(2);
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        private ExecutionId dispatchedExecution() {
            ExecutionId id = start(UUID.randomUUID()).execution().getId();

            await().atMost(Duration.ofSeconds(10)).until(() -> runIdOf(id) != null);

            return id;
        }

        private void writeResult(ExecutionId id, String filename, String content) throws IOException {
            Path path = RESULTS_ROOT.resolve(id.value().toString()).resolve(filename);

            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
        }
    }

    // 커밋 직후 발행이 nack 을 받아 넘기지 못한 실행이다. 그 시도가 끝난 뒤로는 발행이 confirm 을 받는다.
    private ExecutionId undispatchedExecution() {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "nack")).given(runQueuePublisher).submit(any());
        ExecutionId id = start(UUID.randomUUID()).execution().getId();

        then(runQueuePublisher).should(timeout(5_000))
                .submit(argThat(run -> run.executionId().equals(id.value().toString())));
        willDoNothing().given(runQueuePublisher).submit(any());

        return id;
    }

    private ExecutionStart start(UUID requestId) {
        return executionService.start(OWNER, workflow, ExecutionRequestId.of(requestId), null, null, false, null);
    }

    private ExecutionOutboxEntity outboxOf(ExecutionId id) {
        return executionOutboxRepository.findById(id.value()).orElseThrow();
    }

    private String runIdOf(ExecutionId id) {
        return executionRepository.findById(id.value()).map(ExecutionEntity::getRunId).orElse(null);
    }

    private ExecutionStatus statusOf(ExecutionId id) {
        return executionRepository.findById(id.value()).orElseThrow().getStatus();
    }

    // 근거는 닫기가 커밋된 뒤 근거 스레드가 적는다.
    private ExecutionRunRecordEntity recordOf(ExecutionId id) {
        return await().atMost(Duration.ofSeconds(30))
                .until(() -> executionRunRecordRepository.findById(id.value()), java.util.Optional::isPresent)
                .orElseThrow();
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void charge(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.findByAccountTypeAndReferenceId(
                        LedgerAccountType.ABC_BALANCE, OWNER_REFERENCE_ID)
                .orElseGet(() -> ledgerAccountRepository.saveAndFlush(
                        LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, OWNER_REFERENCE_ID)));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now()));
    }

    private BigDecimal balanceOf(LedgerAccountType accountType, UserId user) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(user.value()))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }
}
