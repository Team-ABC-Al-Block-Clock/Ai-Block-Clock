package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.file.UploadedFile;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.enums.execution.ExecutionFault;
import com.aiblockclock.enums.execution.ExecutionResultFormat;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

// 접수(B1 ~ B4) · 진행 조회(B44 · B54) · 결과 받기(B45)와 리스너 · 복구가 부르는 입구(B12 ~ B16 · B30 · B42)다.
// 협력 객체는 목이고, 트랜잭션 · DB 위의 동작은 ExecutionMessagingIntegrationTest · ExecutionControllerTest 가 본다.
class ExecutionServiceTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Instant NOW = Instant.parse("2026-10-16T05:00:00Z");
    private static final UserId REQUESTER = UserId.of(7001L);
    private static final UserId STRANGER = UserId.of(7002L);
    private static final String RAW_INPUT = "{ \"brand_name\" : \"하루\" }";
    private static final String CHECKED_INPUT = "{\"brand_name\":\"하루\"}";

    private final ExecutionPlanAssembler executionPlanAssembler = mock(ExecutionPlanAssembler.class);
    private final ExecutionFileValidator executionFileValidator = mock(ExecutionFileValidator.class);
    private final ExecutionInputValidator executionInputValidator = mock(ExecutionInputValidator.class);
    private final ExecutionFileStore executionFileStore = mock(ExecutionFileStore.class);
    private final ExecutionReservationManager executionReservationManager = mock(ExecutionReservationManager.class);
    private final ExecutionWriter executionWriter = mock(ExecutionWriter.class);
    private final ExecutionReader executionReader = mock(ExecutionReader.class);
    private final ExecutionTracker executionTracker = mock(ExecutionTracker.class);
    private final ExecutionResultCollector executionResultCollector = mock(ExecutionResultCollector.class);
    private final ExecutionResultReader executionResultReader = mock(ExecutionResultReader.class);
    private final ExecutionResultStore executionResultStore = mock(ExecutionResultStore.class);
    private final ExecutionProgressStore progressStore = new ExecutionProgressStore();
    private final AiRuntimeClient aiRuntimeClient = mock(AiRuntimeClient.class);

    private final ExecutionService sut = new ExecutionService(executionPlanAssembler, executionFileValidator,
            executionInputValidator, executionFileStore, executionReservationManager, executionWriter,
            executionReader, executionTracker, executionResultCollector, executionResultReader, executionResultStore,
            progressStore, aiRuntimeClient, Clock.fixed(NOW, ZoneOffset.UTC));

    private final ExecutionId id = ExecutionId.newId();

    @Nested
    class 실행을_맡긴다 {
        private final Workflow workflow = WorkflowFixtures.owned(REQUESTER, GraphFixtures.straightLine());
        private final ExecutionPlan plan = ExecutionPlanner.plan(new UnitGraph(List.of(
                new PlacedUnit(NodeKey.of("n1"), new NodeUnit(InputSource.PREV, UserId.of(8001L), "성적 분석",
                        Prompt.of("점수를 비교한다"), AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null),
                        List.of(), AbcAmount.of(new BigDecimal("0.02"))))),
                List.of()));
        private final ExecutionRequestId requestId = ExecutionRequestId.of(UUID.randomUUID());
        private final UploadedFile upload = new UploadedFile("2학기 성적.xlsx", new byte[] {1, 2, 3});

        // B4 — 같은 요청 번호로 다시 오면 다시 떼지도, outbox 에 적지도, 파일을 보관하지도 않는다.
        @Test
        void 같은_요청_번호로_앞서_맡긴_실행이_있으면_그_실행을_돌려주고_다시_떼지도_적지도_않는다() {
            Execution earlier = execution(REQUESTER, id.value().toString(), ExecutionStatus.RUNNING);
            given(executionReader.find(REQUESTER, requestId)).willReturn(Optional.of(earlier));

            ExecutionStart start = sut.start(REQUESTER, workflow, requestId, RAW_INPUT, null, false, upload);

            assertThat(start.created()).isFalse();
            assertThat(start.execution()).isSameAs(earlier);
            then(executionReservationManager).shouldHaveNoInteractions();
            then(executionWriter).shouldHaveNoInteractions();
            then(executionFileStore).shouldHaveNoInteractions();
            then(executionFileValidator).shouldHaveNoInteractions();
        }

        // B1 — 새 요청은 QUEUED 로 접수해 떼어 둔 실행을 돌려준다.
        @Test
        void 새_요청이면_요청한_실행을_떼어_두고_떼어_둔_실행을_새로_만든_실행으로_돌려준다() {
            givenNewRequest();
            Execution reserved = execution(REQUESTER, null, ExecutionStatus.QUEUED);
            given(executionReservationManager.reserve(any(), eq(workflow), eq(plan))).willReturn(reserved);

            ExecutionStart start = sut.start(REQUESTER, workflow, requestId, RAW_INPUT, "비교해 주세요", true, null);

            assertThat(start.created()).isTrue();
            assertThat(start.execution()).isSameAs(reserved);
            assertThat(requestedToReserve()).satisfies(requested -> {
                assertThat(requested.getRequestId()).isEqualTo(requestId);
                assertThat(requested.getWorkflowId()).isEqualTo(workflow.getId());
                assertThat(requested.getRequester()).isEqualTo(REQUESTER);
                assertThat(requested.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
                assertThat(requested.getRunId()).isNull();
                assertThat(requested.getPromptCount()).isEqualTo(plan.promptCount());
                assertThat(requested.getInputFile()).isNull();
                assertThat(requested.isMock()).isTrue();
            });
        }

        // B2 — outbox 에 적는 SubmitRun 은 떼어 둔 바로 그 실행의 것이다.
        @Test
        void outbox_에는_맡긴_실행의_번호와_검증한_입력으로_만든_SubmitRun_을_적는다() {
            givenNewRequest();
            given(executionReservationManager.reserve(any(), eq(workflow), eq(plan)))
                    .willReturn(execution(REQUESTER, null, ExecutionStatus.QUEUED));

            sut.start(REQUESTER, workflow, requestId, RAW_INPUT, "비교해 주세요", true, null);

            ExecutionId requested = requestedToReserve().getId();
            assertThat(submittedTo(requested)).satisfies(submit -> {
                assertThat(submit.executionId()).isEqualTo(requested.value().toString());
                assertThat(submit.mode()).isEqualTo(RunMode.RUN);
                assertThat(submit.workflow()).isEqualTo(MAPPER.readTree(RuntimeWorkflow.of(workflow, plan).json()));
                assertThat(submit.input()).isEqualTo(MAPPER.readTree(CHECKED_INPUT));
                assertThat(submit.comment()).isEqualTo("비교해 주세요");
                assertThat(submit.mock()).isTrue();
                assertThat(submit.files()).isEmpty();
                assertThat(submit.submittedAt()).isEqualTo(NOW);
            });
            then(executionFileStore).should(never()).write(any(), any());
        }

        // B2 — 올린 파일은 실어 보내지 않고 file-store 의 키만 싣는다.
        @Test
        void 올린_파일은_검사하고_보관한_뒤_그_키를_files_로_싣는다() {
            givenNewRequest();
            ExecutionFile stored = new ExecutionFile("executions/" + id.value() + "/input.xlsx", "2학기 성적.xlsx", 3);
            given(executionFileStore.write(any(), eq(upload))).willReturn(stored);
            given(executionReservationManager.reserve(any(), eq(workflow), eq(plan)))
                    .willReturn(execution(REQUESTER, null, ExecutionStatus.QUEUED));

            sut.start(REQUESTER, workflow, requestId, RAW_INPUT, null, false, upload);

            Execution requested = requestedToReserve();
            then(executionFileValidator).should().validate(upload);
            then(executionFileStore).should().write(requested.getId(), upload);
            assertThat(requested.getInputFile()).isEqualTo(stored);
            assertThat(submittedTo(requested.getId()).files()).containsExactly(stored.key());
        }

        // B3 — 잔액이 모자라면 실행 · outbox 가 남지 않고 올린 파일도 지운다.
        @Test
        void 잔액이_모자라면_같은_예외를_던지고_보관한_파일을_지우고_outbox_에_적지_않는다() {
            givenNewRequest();
            ExecutionFile stored = new ExecutionFile("executions/" + id.value() + "/input.xlsx", "2학기 성적.xlsx", 3);
            CoreException insufficient = new CoreException(ErrorType.ABC_BALANCE_INSUFFICIENT);
            given(executionFileStore.write(any(), eq(upload))).willReturn(stored);
            given(executionReservationManager.reserve(any(), eq(workflow), eq(plan))).willThrow(insufficient);

            assertThatThrownBy(() -> sut.start(REQUESTER, workflow, requestId, RAW_INPUT, null, false, upload))
                    .isSameAs(insufficient);

            then(executionFileStore).should().erase(stored);
            then(executionWriter).should(never()).appendOutbox(any(), any());
        }

        private void givenNewRequest() {
            given(executionReader.find(REQUESTER, requestId)).willReturn(Optional.empty());
            given(executionInputValidator.validate(workflow.getGraph(), RAW_INPUT)).willReturn(CHECKED_INPUT);
            given(executionPlanAssembler.assemble(workflow)).willReturn(plan);
        }

        private Execution requestedToReserve() {
            ArgumentCaptor<Execution> requested = ArgumentCaptor.forClass(Execution.class);
            then(executionReservationManager).should().reserve(requested.capture(), eq(workflow), eq(plan));
            return requested.getValue();
        }

        private SubmitRun submittedTo(ExecutionId requested) {
            ArgumentCaptor<SubmitRun> submit = ArgumentCaptor.forClass(SubmitRun.class);
            then(executionWriter).should().appendOutbox(eq(requested), submit.capture());
            return submit.getValue();
        }
    }

    // B44 — 진행 저장소에 없는 끝나지 않은 실행은 엔진에 묻지도 닫지도 않고 DB 상태로 답한다. 끝난 실행은 기존대로다.
    @Nested
    class 진행_상황을_답한다 {
        @Test
        void 진행_저장소에_있으면_그_진행_상황으로_답한다() {
            ExecutionProgress latest = ExecutionProgress.recorded(id, ExecutionStatus.RUNNING, 5);
            progressStore.put(latest);

            assertThat(sut.progressOf(execution(REQUESTER, id.value().toString(), ExecutionStatus.RUNNING)))
                    .isSameAs(latest);

            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 진행_저장소에_없는_대기_중인_실행은_대기_상태로_답하고_엔진에_묻지_않는다() {
            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, null, ExecutionStatus.QUEUED));

            assertThat(progress).isEqualTo(ExecutionProgress.recorded(id, ExecutionStatus.QUEUED, 2));
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 진행_저장소에_없는_RUNNING_실행은_DB_상태로_답하고_엔진에_묻지_않는다() {
            ExecutionProgress progress =
                    sut.progressOf(execution(REQUESTER, id.value().toString(), ExecutionStatus.RUNNING));

            assertThat(progress).isEqualTo(ExecutionProgress.recorded(id, ExecutionStatus.RUNNING, 2));
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        // 넘기지 못해 버려진 실행이다(B10). 엔진은 이 실행을 모른다.
        @Test
        void 넘기지_못한_채_닫힌_실행은_엔진에_묻지_않고_남긴_상태로_답한다() {
            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, null, ExecutionStatus.FAILED));

            assertThat(progress).isEqualTo(ExecutionProgress.recorded(id, ExecutionStatus.FAILED, 2));
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 끝난_실행은_엔진이_알린_단계에_우리가_닫은_상태를_입혀_답한다() {
            String runId = id.value().toString();
            RunStatus run = RunEventFixtures.succeeded(runId, 1, 3, "8001").snapshot();
            given(aiRuntimeClient.read(runId)).willReturn(run);

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.SUCCEEDED));

            assertThat(progress).isEqualTo(ExecutionProgress.concluded(ExecutionProgress.of(id, run, 2),
                    ExecutionStatus.SUCCEEDED));
            assertThat(progress.steps()).hasSize(2);
        }

        @Test
        void 엔진이_끝난_실행을_잊었으면_남긴_상태로_답한다() {
            String runId = id.value().toString();
            given(aiRuntimeClient.read(runId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.SUCCEEDED));

            assertThat(progress).isEqualTo(ExecutionProgress.recorded(id, ExecutionStatus.SUCCEEDED, 2));
        }

        @Test
        void 남의_실행을_따라가려_하면_EXECUTION_NOT_FOUND_다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.QUEUED));

            assertThatThrownBy(() -> sut.follow(id, STRANGER))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));
        }

        @Test
        void 내_실행을_따라가면_그_실행의_진행_상황으로_답한다() {
            ExecutionProgress latest = ExecutionProgress.recorded(id, ExecutionStatus.RUNNING, 5);
            progressStore.put(latest);
            given(executionReader.read(id)).willReturn(execution(REQUESTER, id.value().toString(),
                    ExecutionStatus.RUNNING));

            assertThat(sut.follow(id, REQUESTER)).isSameAs(latest);
        }
    }

    // B54 — 끝난 FAILED 실행을 진행 저장소 없이 다시 읽으면(새로고침 · 재접속) 실패 사유는 닫을 때 적은 실패 판정
    // (실패 코드 · 책임 · 실패 단계)이다. 엔진이 모르거나(dead letter · LOST), 다른 상태를 답하거나(STALLED 로 끊음),
    // 닿지 않아도 그렇다. 엔진이 단계 목록을 주면 단계 표시는 그 목록을 쓴다.
    @Nested
    class 끝난_실패_실행을_다시_읽는다 {
        private final String runId = id.value().toString();

        // dead letter 로 닫힌 실행은 런타임이 받은 적이 없어 엔진이 늘 모른다(E2E 버그 1).
        @Test
        void dead_letter_로_닫혀_엔진이_모르는_실행은_닫을_때_적은_실패_코드와_책임으로_답한다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, runId, ExecutionStatus.FAILED));
            given(executionReader.readFailure(id)).willReturn(Optional.of(platformFailure(null, "ABC_QUEUE_EXPIRED")));
            given(aiRuntimeClient.read(runId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));

            ExecutionProgress progress = sut.follow(id, REQUESTER);

            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure())
                    .isEqualTo(new ExecutionProgress.Failure(null, "ABC_QUEUE_EXPIRED", "platform"));
        }

        // 감시가 기한을 넘겨 끊었는데 엔진은 아직 돈다고 답한다.
        @Test
        void STALLED_로_끊은_실행은_엔진이_아직_돈다고_답해도_닫을_때_적은_실패_코드와_책임으로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(platformFailure("n2", "ABC_RUNTIME_STALLED")));
            given(aiRuntimeClient.read(runId)).willReturn(RunEventFixtures.progress(runId, 1, 2, "8001").snapshot());

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure().errorCode()).isEqualTo("ABC_RUNTIME_STALLED");
            assertThat(progress.failure().fault()).isEqualTo("platform");
        }

        // n1 에서 끊었는데 엔진은 그 뒤 n2 로 넘어가 있다. 실패 단계는 엔진이 지금 가리키는 단계가 아니라 적은 단계다.
        @Test
        void 엔진이_단계_목록을_주면_그_목록으로_보이고_실패_단계는_닫을_때_적은_단계의_이름이다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(platformFailure("n1", "ABC_RUNTIME_STALLED")));
            given(aiRuntimeClient.read(runId)).willReturn(RunEventFixtures.progress(runId, 1, 2, "8001").snapshot());

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.steps()).extracting(ExecutionProgress.Step::name)
                    .containsExactly("성적 분석", "가정통신문 작성");
            assertThat(progress.failure().stepName()).isEqualTo("성적 분석");
        }

        @Test
        void 엔진에_닿지_못해도_닫을_때_적은_실패_코드와_책임으로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(platformFailure("n2", "ABC_RUNTIME_LOST")));
            given(aiRuntimeClient.read(runId))
                    .willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "connection refused"));

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure().errorCode()).isEqualTo("ABC_RUNTIME_LOST");
            assertThat(progress.failure().fault()).isEqualTo("platform");
        }

        // 엔진이 끝을 알려 닫은 실행이다. 엔진이 답한 실패와 적은 판정이 같으니 지금처럼 답한다.
        @Test
        void 엔진이_알린_실패로_닫은_실행은_엔진이_같은_실패를_답하면_그대로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(new ExecutionFailure(
                    "n2", "MODEL_TIMEOUT", ExecutionFault.SUPPLIER, true, UserId.of(8001L), null)));
            given(aiRuntimeClient.read(runId)).willReturn(RunEventFixtures.failed(runId, 1, 3, "8001").snapshot());

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(progress.failure())
                    .isEqualTo(new ExecutionProgress.Failure("가정통신문 작성", "MODEL_TIMEOUT", "supplier"));
            assertThat(progress.steps()).extracting(ExecutionProgress.Step::name)
                    .containsExactly("성적 분석", "가정통신문 작성");
        }

        // 감시가 STALLED 로 끊은 뒤 엔진이 늦게 다른 실패로 끝냈다. 돈은 닫을 때의 판정으로 정리했으니 답도 그 판정이다.
        @Test
        void 끊은_뒤에_엔진이_다른_실패로_끝냈다고_답해도_닫을_때_적은_판정으로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(platformFailure("n2", "ABC_RUNTIME_STALLED")));
            given(aiRuntimeClient.read(runId)).willReturn(RunEventFixtures.failed(runId, 1, 3, "8001").snapshot());

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.failure())
                    .isEqualTo(new ExecutionProgress.Failure("가정통신문 작성", "ABC_RUNTIME_STALLED", "platform"));
        }

        // 넘기지 못해 버린 실행이다(B10). 엔진은 이 실행을 모르니 묻지 않는다.
        @Test
        void 넘기지_못해_닫힌_실행은_엔진에_묻지_않고_닫을_때_적은_판정으로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.of(ExecutionFailure.undispatched()));

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, null, ExecutionStatus.FAILED));

            assertThat(progress.failure())
                    .isEqualTo(new ExecutionProgress.Failure(null, "ABC_RUNTIME_UNREACHABLE", "platform"));
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        // 울타리 — 판정이 남지 않은 실행은 까닭을 되찾지 못한 것이다. 책임을 지어내지 않는다.
        @Test
        void 실패_판정이_남지_않은_실행을_엔진도_모르면_까닭을_알리지_않은_실패로_답한다() {
            given(executionReader.readFailure(id)).willReturn(Optional.empty());
            given(aiRuntimeClient.read(runId)).willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone"));

            ExecutionProgress progress = sut.progressOf(execution(REQUESTER, runId, ExecutionStatus.FAILED));

            assertThat(progress.failure())
                    .isEqualTo(new ExecutionProgress.Failure(null, "ABC_RUNTIME_UNREPORTED", null));
        }

        private ExecutionFailure platformFailure(String stepId, String errorCode) {
            return new ExecutionFailure(stepId, errorCode, ExecutionFault.PLATFORM, false, null, null);
        }
    }

    // B45 — 결과 받기는 보관한 결과를 주고, 없으면 결과 볼륨에서 다시 가져온다. 엔진 HTTP 는 부르지 않는다.
    @Nested
    class 결과를_받는다 {
        private final ExecutionResult result =
                new ExecutionResult(id, ExecutionResultFormat.PDF, "executions/" + id.value() + "/result.pdf", 10);

        @Test
        void 남의_실행의_결과를_받으려_하면_EXECUTION_NOT_FOUND_다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.SUCCEEDED));

            assertThatThrownBy(() -> sut.download(id, STRANGER, ExecutionResultFormat.PDF))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_NOT_FOUND));

            then(executionResultReader).shouldHaveNoInteractions();
        }

        @Test
        void 아직_도는_실행의_결과를_받으려_하면_EXECUTION_RESULT_NOT_READY_다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.RUNNING));

            assertThatThrownBy(() -> sut.download(id, REQUESTER, ExecutionResultFormat.PDF))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_RESULT_NOT_READY));
        }

        @Test
        void 실패한_실행의_결과를_받으려_하면_EXECUTION_RESULT_UNAVAILABLE_다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.FAILED));

            assertThatThrownBy(() -> sut.download(id, REQUESTER, ExecutionResultFormat.PDF))
                    .isInstanceOfSatisfying(CoreException.class, exception ->
                            assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_RESULT_UNAVAILABLE));
        }

        @Test
        void 보관한_결과가_있으면_결과_볼륨에서_다시_가져오지_않고_그_결과를_준다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.SUCCEEDED));
            given(executionResultReader.read(id, ExecutionResultFormat.PDF)).willReturn(Optional.of(result));

            assertThat(sut.download(id, REQUESTER, ExecutionResultFormat.PDF)).isSameAs(result);

            then(executionResultCollector).shouldHaveNoInteractions();
        }

        @Test
        void 보관한_결과가_없으면_결과_볼륨에서_다시_가져와_그_결과를_준다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.SUCCEEDED));
            given(executionResultReader.read(id, ExecutionResultFormat.PDF))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(result));

            assertThat(sut.download(id, REQUESTER, ExecutionResultFormat.PDF)).isSameAs(result);

            then(executionResultCollector).should().collect(id);
            then(aiRuntimeClient).shouldHaveNoInteractions();
        }

        @Test
        void 결과_볼륨에도_없으면_어느_형식이_없는지와_함께_EXECUTION_RESULT_NOT_FOUND_다() {
            given(executionReader.read(id)).willReturn(execution(REQUESTER, null, ExecutionStatus.SUCCEEDED));
            given(executionResultReader.read(id, ExecutionResultFormat.PDF)).willReturn(Optional.empty());

            assertThatThrownBy(() -> sut.download(id, REQUESTER, ExecutionResultFormat.PDF))
                    .isInstanceOfSatisfying(CoreException.class, exception -> {
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.EXECUTION_RESULT_NOT_FOUND);
                        assertThat(exception.getData()).isEqualTo(Map.of("format", "PDF"));
                    });

            then(executionResultCollector).should().collect(id);
        }

        @Test
        void 결과_파일의_자리는_결과_보관소가_정한_자리다() {
            Path stored = Path.of("results", id.value().toString(), "result.pdf");
            given(executionResultStore.locate(result)).willReturn(stored);

            assertThat(sut.locate(result)).isEqualTo(stored);
        }
    }

    // 리스너(B12 ~ B16 · B30)와 복구(B42)가 부르는 입구다. 일은 ExecutionTracker 가 한다.
    @Nested
    class 이벤트와_복구를_넘긴다 {
        @Test
        void 복구가_읽는_끝나지_않은_실행은_Reader_가_읽은_그대로다() {
            UnfinishedExecution running = new UnfinishedExecution(
                    execution(REQUESTER, id.value().toString(), ExecutionStatus.RUNNING), NOW);
            given(executionReader.readUnfinished()).willReturn(List.of(running));

            assertThat(sut.readUnfinished()).containsExactly(running);
        }

        @Test
        void 실행_이벤트는_추적기에_넘겨_적용한다() {
            RunEvent event = RunEventFixtures.accepted(id.value().toString(), 1, 1);

            sut.applyEvent(event);

            then(executionTracker).should().apply(event);
        }

        @Test
        void dead_letter_로_온_실행은_추적기에_넘겨_그_사유로_닫는다() {
            sut.closeDeadLettered(id, DeadLetterReason.EXPIRED);

            then(executionTracker).should().closeDeadLettered(id, DeadLetterReason.EXPIRED);
        }

        // B6 · B14 — 복구 스케줄러가 넘긴 채 QUEUED 인 실행을 넘긴다. 한도가 지났는지 보고 닫는 일은 추적기가 한다.
        @Test
        void 큐에서_기다리는_실행은_추적기에_넘겨_큐_대기_한도가_지났으면_닫게_한다() {
            sut.closeOverdueInQueue(id);

            then(executionTracker).should().closeOverdueInQueue(id);
        }
    }

    private Execution execution(UserId requester, String runId, ExecutionStatus status) {
        return Execution.restore(id, ExecutionRequestId.of(UUID.randomUUID()), WorkflowId.of(UUID.randomUUID()),
                requester, runId == null ? null : RunId.of(runId), status, 2, null, false,
                AbcAmount.of(new BigDecimal("6")));
    }
}
