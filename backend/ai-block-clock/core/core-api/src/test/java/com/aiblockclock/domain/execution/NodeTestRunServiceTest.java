package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionFixtures;
import com.aiblockclock.domain.node.NodeDefinitionReader;
import com.aiblockclock.domain.node.NodeTestRun;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.node.NodeTestRunManager;
import com.aiblockclock.domain.node.NodeTestRunOutcome;
import com.aiblockclock.domain.node.NodeTestRunReader;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.node.RefFileReader;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

// 노드 테스트도 실행처럼 브로커로 맡기고 이벤트로 닫는다(ADR-0001). 다만 판매자가 응답을 기다리므로 발행은 confirm 까지 기다린다.
// 엔진 HTTP 는 접수 시각에서 기한(단계 하나라 60초 + 60초 = 2분)을 넘긴 테스트를 조회할 때만 쓴다.
class NodeTestRunServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final UserId OWNER = UserId.of(7001L);
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final Duration DEADLINE = Duration.ofMinutes(2);

    private final NodeTestRunManager manager = mock(NodeTestRunManager.class);
    private final NodeTestRunReader testRunReader = mock(NodeTestRunReader.class);
    private final NodeDefinitionReader nodeReader = mock(NodeDefinitionReader.class);
    private final RefFileReader refFileReader = mock(RefFileReader.class);
    private final RunQueuePublisher runQueuePublisher = mock(RunQueuePublisher.class);
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);

    private final NodeTestRunService sut = new NodeTestRunService(manager, testRunReader, nodeReader, refFileReader,
            runQueuePublisher, aiRuntimeClient,
            new ExecutionWatchProperties(Duration.ofSeconds(60), Duration.ofSeconds(60)), Clock.fixed(NOW, ZoneOffset.UTC));

    private NodeDefinition node;

    private NodeTestRun reserved;

    private String testRunId;

    @BeforeEach
    void 노드_하나와_접수한_테스트를_둔다() {
        node = NodeDefinitionFixtures.of(OWNER);
        reserved = testRun(null, NodeTestRunStatus.REQUESTED, NOW);
        testRunId = reserved.id().value().toString();

        given(nodeReader.read(node.getId())).willReturn(node);
        given(refFileReader.read(node.getId())).willReturn(List.of());
        given(manager.reserve(eq(node.getId()), eq(OWNER), anyBoolean())).willReturn(reserved);
    }

    @Nested
    class 테스트를_맡긴다 {

        @Test
        void 노드_하나를_test_모드로_발행하고_confirm_을_받은_뒤_테스트_실행_번호를_runId_로_남긴다() {
            NodeTestRun dispatched = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW);
            given(manager.dispatch(reserved.id(), testRunId)).willReturn(dispatched);

            NodeTestRunResult result = sut.start(node.getId(), OWNER, "2학기 점수를 비교해 주세요", false);

            InOrder order = inOrder(runQueuePublisher, manager);
            order.verify(runQueuePublisher).submit(any(SubmitRun.class));
            order.verify(manager).dispatch(reserved.id(), testRunId);
            assertThat(result.run()).isSameAs(dispatched);
            assertThat(result.baseCost()).isEqualTo(node.getSpec().baseCost());
        }

        @Test
        void 발행하는_SubmitRun_은_테스트_실행_번호와_test_모드와_노드_하나짜리_워크플로우를_싣는다() {
            sut.start(node.getId(), OWNER, "2학기 점수를 비교해 주세요", true);

            SubmitRun sent = submitted();
            assertThat(sent.executionId()).isEqualTo(testRunId);
            assertThat(sent.mode()).isEqualTo(RunMode.TEST);
            assertThat(sent.workflow().isObject()).isTrue();
            assertThat(sent.workflow().get("id").asString()).isEqualTo(testRunId);
            assertThat(sent.workflow().get("nodes")).hasSize(1);
            assertThat(sent.workflow().get("nodes").get(0).get("id").asString()).isEqualTo("n1");
            assertThat(sent.workflow().get("nodes").get(0).get("prompt").asString())
                    .isEqualTo("당신은 학원 성적 데이터 분석가입니다.");
            assertThat(sent.input()).isNull();
            assertThat(sent.comment()).isEqualTo("2학기 점수를 비교해 주세요");
            assertThat(sent.mock()).isTrue();
            assertThat(sent.files()).isEmpty();
            assertThat(sent.refFiles()).isEmpty();
        }

        @Test
        void 노드의_참고_자료는_단계_n1_에_file_store_키로_싣고_엔진이_받지_않는_형식은_뺀다() {
            RefFile guide = new RefFile(RefFileId.newId(), "nodes/" + node.getId().value() + "/guide.pdf",
                    "기준표.pdf", 10);
            RefFile image = new RefFile(RefFileId.newId(), "nodes/" + node.getId().value() + "/logo.png",
                    "로고.png", 10);
            given(refFileReader.read(node.getId())).willReturn(List.of(guide, image));

            sut.start(node.getId(), OWNER, null, false);

            assertThat(submitted().refFiles()).containsExactlyEntriesOf(
                    Map.of("n1", List.of(guide.key())));
        }

        @Test
        void 엔진_HTTP_로_맡기지_않는다() {
            sut.start(node.getId(), OWNER, null, false);

            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 발행이_실패하면_접수를_지워_하루_횟수를_쓰지_않고_EXECUTION_ENGINE_UNAVAILABLE_로_알린다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "nack"))
                    .given(runQueuePublisher).submit(any());

            assertThatThrownBy(() -> sut.start(node.getId(), OWNER, null, false))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_ENGINE_UNAVAILABLE));

            then(manager).should().discard(reserved.id());
            then(manager).should(never()).dispatch(any(), anyString());
        }

        // B25 보강 — confirm 을 못 받았어도 브로커는 받았을 수 있다. 런타임이 늦게 받아 돌리지 않게 그 테스트 번호로 취소를 건다.
        @Test
        void 발행이_실패하면_그_테스트_번호로_취소를_걸고_접수를_지우고_EXECUTION_ENGINE_UNAVAILABLE_로_알린다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "confirm timed out"))
                    .given(runQueuePublisher).submit(any());

            assertThatThrownBy(() -> sut.start(node.getId(), OWNER, null, false))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_ENGINE_UNAVAILABLE));

            then(runQueuePublisher).should().cancel(testRunId);
            then(manager).should().discard(reserved.id());
        }

        @Test
        void 취소를_걸지_못해도_접수를_지우고_EXECUTION_ENGINE_UNAVAILABLE_로_알린다() {
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "confirm timed out"))
                    .given(runQueuePublisher).submit(any());
            willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "broker unresponsive"))
                    .given(runQueuePublisher).cancel(anyString());

            assertThatThrownBy(() -> sut.start(node.getId(), OWNER, null, false))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_ENGINE_UNAVAILABLE));

            then(runQueuePublisher).should().cancel(testRunId);
            then(manager).should().discard(reserved.id());
        }

        // 울타리 — 넘긴 테스트는 취소하지 않는다.
        @Test
        void 발행이_confirm_을_받으면_취소를_걸지_않는다() {
            sut.start(node.getId(), OWNER, null, false);

            then(runQueuePublisher).should(never()).cancel(anyString());
        }

        // 런타임은 이미 받았다. 접수를 지우면 그 실행으로 잰 원가도 결과물도 잃는다.
        @Test
        void confirm_을_받은_뒤에_기록이_실패하면_접수를_지우지_않는다() {
            given(manager.dispatch(reserved.id(), testRunId)).willThrow(new IllegalStateException("db down"));

            assertThatThrownBy(() -> sut.start(node.getId(), OWNER, null, false))
                    .isInstanceOf(IllegalStateException.class);

            then(manager).should(never()).discard(any());
        }
    }

    @Nested
    class 이벤트로_닫는다 {

        @BeforeEach
        void 맡긴_테스트가_돌고_있다() {
            given(testRunReader.read(reserved.id())).willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW));
        }

        @Test
        void succeeded_가_오면_snapshot_합계_원가와_결과물로_SUCCEEDED_로_닫는다() {
            sut.applyEvent(testEvent(RunEventType.SUCCEEDED, succeededSnapshot(
                    "\"totals\":{\"cost_usd\":0.004}", "0.003", "0.001"), RunEventFixtures.ARTIFACT));

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.SUCCEEDED);
            assertThat(outcome.costUsd()).isEqualByComparingTo("0.004");
            assertThat(outcome.failureCode()).isNull();
            assertThat(MAPPER.readTree(outcome.resultJson())).isEqualTo(MAPPER.readTree(RunEventFixtures.ARTIFACT));
        }

        @Test
        void 합계가_없으면_단계마다_잰_원가를_더한다() {
            sut.applyEvent(testEvent(RunEventType.SUCCEEDED, succeededSnapshot(null, "0.003", "0.001"),
                    RunEventFixtures.ARTIFACT));

            assertThat(closedWith().costUsd()).isEqualByComparingTo("0.004");
        }

        // 고쳐 쓰기처럼 단계 목록에 잡히지 않는 원가가 있을 수 있다. 합계가 있으면 합계를 믿는다.
        @Test
        void 합계가_단계마다_잰_원가의_합과_다르면_합계를_원가로_쓴다() {
            sut.applyEvent(testEvent(RunEventType.SUCCEEDED, succeededSnapshot(
                    "\"totals\":{\"cost_usd\":0.005}", "0.003", "0.001"), RunEventFixtures.ARTIFACT));

            assertThat(closedWith().costUsd()).isEqualByComparingTo("0.005");
        }

        @Test
        void 결과물이_비어_있으면_결과물_없이_닫는다() {
            sut.applyEvent(testEvent(RunEventType.SUCCEEDED, succeededSnapshot("\"totals\":{\"cost_usd\":0.004}",
                    "0.004", null), null));

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.SUCCEEDED);
            assertThat(outcome.resultJson()).isNull();
        }

        @Test
        void failed_가_오면_snapshot_의_실패_코드로_FAILED_로_닫고_결과물은_남기지_않는다() {
            sut.applyEvent(testEvent(RunEventType.FAILED, """
                    {"run_id":"%s","status":"failed",
                     "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"failed","cost_usd":0.001}],
                     "failure":{"node_id":"n1","error_code":"MODEL_TIMEOUT","fault":"supplier","affects_success_rate":true},
                     "totals":{"cost_usd":0.001}}""".formatted(testRunId), null));

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.FAILED);
            assertThat(outcome.failureCode()).isEqualTo("MODEL_TIMEOUT");
            assertThat(outcome.resultJson()).isNull();
        }

        @Test
        void rejected_도_snapshot_의_실패_코드로_FAILED_로_닫는다() {
            sut.applyEvent(testEvent(RunEventType.REJECTED, RunEventFixtures.rejectedSnapshot(testRunId), null));

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.FAILED);
            assertThat(outcome.failureCode()).isEqualTo("INVALID_WORKFLOW");
            assertThat(outcome.resultJson()).isNull();
        }

        @Test
        void accepted_와_progress_는_테스트를_바꾸지_않는다() {
            sut.applyEvent(testEvent(RunEventType.ACCEPTED, RunEventFixtures.queuedSnapshot(testRunId), null));
            sut.applyEvent(testEvent(RunEventType.PROGRESS, """
                    {"run_id":"%s","status":"running","progress":{"done":0,"total":1,"percent":0},
                     "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"running"}]}"""
                    .formatted(testRunId), null));

            then(manager).shouldHaveNoInteractions();
        }

        // 테스트 실행 번호는 UUID 다. UUID 가 아닌 번호는 모르는 테스트다 — 실행의 B21 과 같이 넘어간다.
        @Test
        void 테스트_실행_번호가_UUID_가_아닌_이벤트는_모르는_테스트로_보고_던지지_않고_아무것도_바꾸지_않는다() {
            RunEvent unknown = RunEventFixtures.of("not-a-uuid", RunMode.TEST, 1, 2, RunEventType.SUCCEEDED,
                    succeededSnapshot("\"totals\":{\"cost_usd\":0.004}", "0.004", null), RunEventFixtures.ARTIFACT);

            assertThatCode(() -> sut.applyEvent(unknown)).doesNotThrowAnyException();

            then(manager).shouldHaveNoInteractions();
        }

        // 지워졌거나 다른 환경의 테스트다. 다시 시도해도 알게 되지 않는다 — 실행의 B21 과 같이 넘어간다.
        @Test
        void DB_에_없는_테스트의_이벤트는_모르는_테스트로_보고_던지지_않는다() {
            NodeTestRunId unknown = NodeTestRunId.newId();
            given(testRunReader.read(unknown)).willThrow(new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND));
            given(manager.finish(eq(unknown), any())).willThrow(new CoreException(ErrorType.NODE_TEST_RUN_NOT_FOUND));
            RunEvent event = RunEventFixtures.of(unknown.value().toString(), RunMode.TEST, 1, 2, RunEventType.FAILED,
                    RunEventFixtures.rejectedSnapshot(unknown.value().toString()), null);

            assertThatCode(() -> sut.applyEvent(event)).doesNotThrowAnyException();
        }

        // 모르는 테스트만 넘어간다. DB 오류는 리스너가 다시 시도하도록 그대로 던진다(B23).
        @Test
        void 테스트를_닫다_DB_오류가_나면_밖으로_던진다() {
            DataAccessResourceFailureException down = new DataAccessResourceFailureException("db down");
            given(manager.finish(eq(reserved.id()), any())).willThrow(down);

            assertThatThrownBy(() -> sut.applyEvent(testEvent(RunEventType.SUCCEEDED, succeededSnapshot(
                    "\"totals\":{\"cost_usd\":0.004}", "0.004", null), RunEventFixtures.ARTIFACT))).isSameAs(down);
        }

        @ParameterizedTest
        @CsvSource({
                "EXPIRED, ABC_QUEUE_EXPIRED",
                "DELIVERY_LIMIT, ABC_RUNTIME_RETRY_EXHAUSTED",
                "OTHER, ABC_RUNTIME_DEAD_LETTERED"})
        void dead_letter_로_온_테스트는_사유의_코드로_실패로_닫는다(DeadLetterReason reason, String code) {
            given(testRunReader.read(reserved.id())).willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW));

            sut.closeDeadLettered(reserved.id(), reason);

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.FAILED);
            assertThat(outcome.failureCode()).isEqualTo(code);
            assertThat(outcome.resultJson()).isNull();
        }
    }

    @Nested
    class 테스트를_조회한다 {

        @Test
        void 기한_안에_도는_테스트는_저장된_상태로_답하고_엔진을_부르지_않는다() {
            NodeTestRun running = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(DEADLINE).plusMillis(1));
            given(testRunReader.read(reserved.id())).willReturn(running);

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(running);
            then(aiRuntimeClient).shouldHaveNoInteractions();
            then(manager).should(never()).finish(any(), any());
        }

        @Test
        void 접수에서_기한이_되면_엔진에_한_번_묻는다() {
            given(testRunReader.read(reserved.id()))
                    .willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(DEADLINE)));
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("running"));

            sut.follow(node.getId(), reserved.id(), OWNER);

            then(aiRuntimeClient).should(times(1)).read(testRunId);
            then(manager).should(never()).finish(any(), any());
        }

        @Test
        void 기한을_넘긴_테스트가_엔진에서_끝났으면_그_응답과_결과물로_닫는다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5)));
            NodeTestRun closed = testRun(testRunId, NodeTestRunStatus.SUCCEEDED, overdue.requestedAt());
            given(testRunReader.read(reserved.id())).willReturn(overdue);
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("succeeded"));
            given(aiRuntimeClient.readArtifact(testRunId)).willReturn(RunEventFixtures.ARTIFACT);
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.of(closed));

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.SUCCEEDED);
            assertThat(outcome.costUsd()).isEqualByComparingTo("0.004");
            assertThat(MAPPER.readTree(outcome.resultJson())).isEqualTo(MAPPER.readTree(RunEventFixtures.ARTIFACT));
            assertThat(result.run()).isSameAs(closed);
        }

        @Test
        void 기한을_넘긴_테스트를_엔진이_모르면_ABC_RUNTIME_LOST_로_닫는다() {
            given(testRunReader.read(reserved.id()))
                    .willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5))));
            given(aiRuntimeClient.read(testRunId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.empty());

            sut.follow(node.getId(), reserved.id(), OWNER);

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.FAILED);
            assertThat(outcome.failureCode()).isEqualTo("ABC_RUNTIME_LOST");
        }

        @Test
        void 기한을_넘겼어도_엔진이_아직_돌고_있으면_닫지_않고_저장된_테스트로_답한다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(DEADLINE));
            given(testRunReader.read(reserved.id())).willReturn(overdue);
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("running"));

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(overdue);
            then(manager).should(never()).finish(any(), any());
        }

        @Test
        void 기한을_넘긴_테스트를_엔진이_몰라_닫으면_닫힌_테스트로_답한다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5)));
            NodeTestRun closed = testRun(testRunId, NodeTestRunStatus.FAILED, overdue.requestedAt());
            given(testRunReader.read(reserved.id())).willReturn(overdue);
            given(aiRuntimeClient.read(testRunId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.of(closed));

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(closed);
        }

        // 그 사이 다른 조회나 이벤트가 먼저 닫았다. 닫기는 아무것도 하지 않고, 닫힌 테스트를 다시 읽어 답한다.
        @Test
        void 기한을_넘긴_테스트를_엔진이_모르는데_그_사이_닫혔으면_다시_읽은_테스트로_답한다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5)));
            NodeTestRun closedMeanwhile = testRun(testRunId, NodeTestRunStatus.SUCCEEDED, overdue.requestedAt());
            given(testRunReader.read(reserved.id())).willReturn(overdue, closedMeanwhile);
            given(aiRuntimeClient.read(testRunId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.empty());

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(closedMeanwhile);
        }

        @Test
        void 기한을_넘긴_테스트가_엔진에서_끝났는데_그_사이_닫혔으면_다시_읽은_테스트로_답한다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5)));
            NodeTestRun closedMeanwhile = testRun(testRunId, NodeTestRunStatus.SUCCEEDED, overdue.requestedAt());
            given(testRunReader.read(reserved.id())).willReturn(overdue, closedMeanwhile);
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("succeeded"));
            given(aiRuntimeClient.readArtifact(testRunId)).willReturn(RunEventFixtures.ARTIFACT);
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.empty());

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(closedMeanwhile);
        }

        @Test
        void 기한을_넘긴_테스트가_엔진에서_성공했는데_결과물을_받지_못하면_결과물_없이_성공으로_닫는다() {
            given(testRunReader.read(reserved.id()))
                    .willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5))));
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("succeeded"));
            given(aiRuntimeClient.readArtifact(testRunId))
                    .willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.empty());

            sut.follow(node.getId(), reserved.id(), OWNER);

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.SUCCEEDED);
            assertThat(outcome.resultJson()).isNull();
        }

        // 앞단이 오류 페이지를 200 으로 주면 그 본문이 응답에 실려 판매자 화면이 읽지 못한다. 빈 문자열도 남기지 않는다.
        @Test
        void 기한을_넘긴_테스트가_엔진에서_성공했는데_결과물이_JSON_이_아니면_결과물_없이_성공으로_닫는다() {
            given(testRunReader.read(reserved.id()))
                    .willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5))));
            given(aiRuntimeClient.read(testRunId)).willReturn(engineStatus("succeeded"));
            given(aiRuntimeClient.readArtifact(testRunId)).willReturn("<html>502</html>");
            given(manager.finish(eq(reserved.id()), any())).willReturn(Optional.empty());

            sut.follow(node.getId(), reserved.id(), OWNER);

            NodeTestRunOutcome outcome = closedWith();
            assertThat(outcome.status()).isEqualTo(NodeTestRunStatus.SUCCEEDED);
            assertThat(outcome.resultJson()).isNull();
        }

        @Test
        void 기한을_넘긴_테스트라도_엔진에_닿지_못하면_그대로_답한다() {
            NodeTestRun overdue = testRun(testRunId, NodeTestRunStatus.RUNNING, NOW.minus(Duration.ofMinutes(5)));
            given(testRunReader.read(reserved.id())).willReturn(overdue);
            given(aiRuntimeClient.read(testRunId)).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(overdue);
            then(manager).should(never()).finish(any(), any());
        }

        @Test
        void 끝난_테스트는_엔진을_부르지_않는다() {
            NodeTestRun finished = testRun(testRunId, NodeTestRunStatus.SUCCEEDED, NOW.minus(Duration.ofHours(1)));
            given(testRunReader.read(reserved.id())).willReturn(finished);

            NodeTestRunResult result = sut.follow(node.getId(), reserved.id(), OWNER);

            assertThat(result.run()).isSameAs(finished);
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 남의_테스트는_있는지도_알리지_않는다() {
            given(testRunReader.read(reserved.id())).willReturn(testRun(testRunId, NodeTestRunStatus.RUNNING, NOW));

            assertThatThrownBy(() -> sut.follow(node.getId(), reserved.id(), UserId.of(9999L)))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NODE_TEST_RUN_NOT_FOUND));
        }
    }

    private SubmitRun submitted() {
        ArgumentCaptor<SubmitRun> sent = ArgumentCaptor.forClass(SubmitRun.class);

        then(runQueuePublisher).should().submit(sent.capture());

        return sent.getValue();
    }

    private NodeTestRunOutcome closedWith() {
        ArgumentCaptor<NodeTestRunOutcome> outcome = ArgumentCaptor.forClass(NodeTestRunOutcome.class);

        then(manager).should().finish(eq(reserved.id()), outcome.capture());

        return outcome.getValue();
    }

    private RunEvent testEvent(RunEventType type, String snapshotJson, String artifactJson) {
        return RunEventFixtures.of(testRunId, RunMode.TEST, 1, 2, type, snapshotJson, artifactJson);
    }

    private String succeededSnapshot(String totals, String firstCost, String secondCost) {
        String second = secondCost == null ? "" : """
                ,{"id":"n1-repair","name":"고쳐 쓰기","type":"llm","status":"succeeded","cost_usd":%s}"""
                .formatted(secondCost);

        return """
                {"run_id":"%s","status":"succeeded","progress":{"done":1,"total":1,"percent":100},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","cost_usd":%s}%s]%s}"""
                .formatted(testRunId, firstCost, second, totals == null ? "" : "," + totals);
    }

    private RunStatus engineStatus(String state) {
        return new RunStatus(testRunId, state, null,
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", state, null, "7001", new BigDecimal("0.004"), null)),
                List.of(), List.of(), null, null, false, new RunStatus.Totals(new BigDecimal("0.004")));
    }

    private NodeTestRun testRun(String runId, NodeTestRunStatus status, Instant requestedAt) {
        NodeTestRunId id = reserved == null ? NodeTestRunId.newId() : reserved.id();

        return new NodeTestRun(id, node.getId(), OWNER, runId, false, status, null, null, null, requestedAt, null);
    }
}
