package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.execution.DeadLetterReason;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionProgressStore;
import com.aiblockclock.domain.execution.ExecutionService;
import com.aiblockclock.domain.execution.RunEventFixtures;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionOutboxRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실행 접수는 DB(실행 · 선차감 · outbox)에만 쓰고 202 를 준다. 브로커로 넘기는 일은 커밋 뒤 디스패처가 한다(ADR-0001).
// 테스트 트랜잭션 안에서 돌아 커밋되지 않으므로 여기서는 발행이 일어나지 않는다. 발행은 ExecutionMessagingIntegrationTest 가 본다.
// 진행 · 완료는 런타임 이벤트(RunEvent)로 오고, 결과물은 결과 볼륨에서 가져온다.
@Tag("integration")
// 금액을 셈하기 쉽게 새로 만든 노드의 원가를 3 ABC 로 둔다. 기본 원가 자체는 NodeControllerTest 가 본다.
@SpringBootTest(properties = "node.base-cost.default-cost=3")
@AutoConfigureMockMvc
@Transactional
class ExecutionControllerTest {
    private static final Path FILE_ROOT = createTempDirectory("execution-files");
    private static final Path RESULTS_ROOT = createTempDirectory("execution-results");
    private static final String ARTIFACT = "{\"schema_id\":\"common.document@1\"}";
    private static final byte[] PDF = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionOutboxRepository executionOutboxRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private ExecutionProgressStore progressStore;

    @Autowired
    private ExecutionService executionService;

    @MockitoBean
    private RunQueuePublisher runQueuePublisher;

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    private long requester;

    private long other;

    private String workflowId;

    @DynamicPropertySource
    static void 파일과_결과_볼륨은_임시_폴더에_둔다(DynamicPropertyRegistry registry) {
        registry.add("file-store.root", () -> FILE_ROOT);
        registry.add("ai.runtime.results-root", () -> RESULTS_ROOT);
    }

    @BeforeEach
    void 워크플로우를_하나_저장해_둔다() throws Exception {
        clear(FILE_ROOT);
        clear(RESULTS_ROOT);
        requester = createUser();
        other = createUser();

        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "성적 분석",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "성적 분석", "prompt": "점수를 비교한다" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "가정통신문 작성", "prompt": "가정통신문을 쓴다" }
                                  ],
                                  "edges": [ { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" } ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asString();
        charge(requester, "10");
    }

    @Test
    void 실행을_맡기면_202_와_QUEUED_가_나오고_런타임_실행_번호는_아직_비어_있다() throws Exception {
        String executionId = start();

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow())
                .satisfies(execution -> {
                    assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.QUEUED);
                    assertThat(execution.getRunId()).isNull();
                    assertThat(execution.getPromptCount()).isEqualTo(2);
                });
    }

    // 노드 두 개라 한 번 실행에 3 + 3 = 6 ABC 를 뗀다. 잔액은 10 으로 시작한다.
    @Test
    void 실행을_접수하면_견적만큼_쓸_수_있는_잔액에서_예치로_옮긴다() throws Exception {
        start();

        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
    }

    @Test
    void 요청을_처리하는_동안_엔진도_브로커도_부르지_않는다() throws Exception {
        start();

        then(aiRuntimeClient).shouldHaveNoInteractions();
        then(runQueuePublisher).shouldHaveNoInteractions();
    }

    @Test
    void 접수와_함께_그_실행의_SubmitRun_을_outbox_에_남긴다() throws Exception {
        String executionId = start();

        JsonNode submit = submitOf(executionId);
        assertThat(submit.get("execution_id").asString()).isEqualTo(executionId);
        assertThat(submit.get("mode").asString()).isEqualTo("run");
        assertThat(submit.get("mock").booleanValue()).isFalse();
        assertThat(submit.get("input").isNull()).isTrue();
        assertThat(submit.get("comment").isNull()).isTrue();
        assertThat(submit.get("files")).isEmpty();
        assertThat(submit.get("ref_files")).isEmpty();
    }

    @Test
    void 펼친_프롬프트를_런타임이_읽는_워크플로우_객체로_싣는다() throws Exception {
        JsonNode workflow = submitOf(start()).get("workflow");

        assertThat(workflow.isObject()).isTrue();
        assertThat(workflow.get("model").asString()).isEqualTo("gemini-2.5-flash");
        assertThat(workflow.get("nodes")).extracting(node -> node.get("type").asString()).containsOnly("llm");
        assertThat(workflow.get("nodes")).extracting(node -> node.get("prompt").asString())
                .containsExactly("점수를 비교한다", "가정통신문을 쓴다");
        assertThat(workflow.get("nodes")).extracting(node -> node.get("owner_id").asString())
                .containsOnly(Long.toString(requester));
        assertThat(workflow.get("edges").get(0).get("from").asString()).isEqualTo("n1");
    }

    @Test
    void 시작_노드의_입력_값을_객체로_싣고_입출력_노드는_작업_단계로_넣지_않는다() throws Exception {
        workflowId = saveWithStartAndEnd();

        MvcResult accepted = mockMvc.perform(run().param("input", "{\"brand_name\":\"하루\"}")
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();
        String executionId = executionIdOf(accepted);

        JsonNode submit = submitOf(executionId);
        assertThat(submit.get("input")).isEqualTo(mapper.readTree("{\"brand_name\":\"하루\"}"));
        assertThat(submit.get("workflow").get("nodes")).hasSize(1);
        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getPromptCount())
                .isEqualTo(1);
    }

    @Test
    void 코멘트와_mock_을_싣는다() throws Exception {
        MvcResult accepted = mockMvc.perform(run().param("comment", "2학기 점수를 비교해 주세요").param("mock", "true")
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();

        JsonNode submit = submitOf(executionIdOf(accepted));
        assertThat(submit.get("comment").asString()).isEqualTo("2학기 점수를 비교해 주세요");
        assertThat(submit.get("mock").booleanValue()).isTrue();
    }

    // 작업 단계는 LLM 노드 셋(market · high · low)이다. 분기 노드는 단계로 세지 않는다. 견적 3 × 3 = 9 는 잔액 10 안이다.
    @Test
    void 분기가_있는_워크플로우는_분기_노드와_참_거짓_갈래를_싣는다() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "경쟁 강도로 가른다",
                                  "nodes": [
                                    { "nodeKey": "market", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "시장 조사", "prompt": "시장을 조사한다",
                                      "output": { "kind": "JSON", "example": { "competition_level": "high" } } },
                                    { "nodeKey": "br", "sourceType": "CONTROL", "controlType": "BRANCH",
                                      "condition": { "left": "market.competition_level", "operator": "EQ", "right": "high" } },
                                    { "nodeKey": "high", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "고급 전략", "prompt": "고급 전략을 쓴다" },
                                    { "nodeKey": "low", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "보급 전략", "prompt": "보급 전략을 쓴다" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "market", "targetNodeKey": "br" },
                                    { "edgeKey": "e2", "sourceNodeKey": "br", "targetNodeKey": "high", "when": true },
                                    { "edgeKey": "e3", "sourceNodeKey": "br", "targetNodeKey": "low", "when": false }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        workflowId = mapper.readTree(saved.getResponse().getContentAsString()).get("data").get("workflowId").asString();

        MvcResult accepted = mockMvc.perform(run().with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.stepCount").value(3))
                .andReturn();
        String workflow = submitOf(executionIdOf(accepted)).get("workflow").toString();

        assertThat(workflow)
                .contains("\"type\":\"branch\"")
                .contains("\"left\":\"n1.competition_level\"")
                .contains("\"when\":true")
                .contains("\"when\":false")
                .contains("\"example\":{\"competition_level\":\"high\"}");
    }

    @Test
    void 올린_파일을_보관하고_그_file_store_키를_files_로_싣는다() throws Exception {
        MvcResult accepted = mockMvc.perform(run().file(excel("2학기 성적.xlsx", "점수"))
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();
        String executionId = executionIdOf(accepted);
        String key = "executions/" + executionId + "/input.xlsx";

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow())
                .satisfies(execution -> {
                    assertThat(execution.getInputFileKey()).isEqualTo(key);
                    assertThat(execution.getInputFileName()).isEqualTo("2학기 성적.xlsx");
                });
        assertThat(FILE_ROOT.resolve(key)).hasContent("점수");
        assertThat(submitOf(executionId).get("files")).isEqualTo(mapper.readTree("[\"" + key + "\"]"));
    }

    // 실패한 요청이 쓴 실행 행은 테스트 트랜잭션 안에서는 롤백 전까지 보여 여기서 따지지 않는다.
    // 실행이 남지 않는 것은 커밋하는 ExecutionMessagingIntegrationTest 가 본다.
    @Test
    void 잔액이_모자라면_402_와_부족액을_알리고_outbox_도_예치도_늘리지_않는다() throws Exception {
        start();

        mockMvc.perform(run().with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_008"))
                .andExpect(jsonPath("$.error.data.estimatedAmount").value("6.000000"))
                .andExpect(jsonPath("$.error.data.availableAmount").value("4.000000"))
                .andExpect(jsonPath("$.error.data.shortageAmount").value("2.000000"));

        assertThat(outboxCountOf(requester)).isEqualTo(1);
        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
    }

    @Test
    void 잔액이_모자라_접수하지_못하면_올린_파일을_남기지_않는다() throws Exception {
        start();

        mockMvc.perform(run().file(excel("성적.csv", "점수")).with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isPaymentRequired());

        assertThat(FILE_ROOT.resolve("executions")).isEmptyDirectory();
    }

    @Test
    void 같은_요청_번호로_다시_보내면_같은_실행을_돌려주고_한_번만_떼고_outbox_도_하나다() throws Exception {
        String requestId = UUID.randomUUID().toString();

        String first = startWith(requestId);
        String second = startWith(requestId);

        assertThat(second).isEqualTo(first);
        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
        assertThat(outboxCountOf(requester)).isEqualTo(1);
        then(runQueuePublisher).shouldHaveNoInteractions();
    }

    @Test
    void 같은_요청_번호로_다시_보내도_끝난_실행을_다시_따라가지_않는다() throws Exception {
        String requestId = UUID.randomUUID().toString();
        String executionId = startWith(requestId);
        markFinished(executionId, ExecutionStatus.SUCCEEDED);

        startWith(requestId);

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(progressStore.find(ExecutionId.of(executionId))).isEmpty();
    }

    @Test
    void mock_실행도_똑같이_뗀다() throws Exception {
        mockMvc.perform(run().param("mock", "true").with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted());

        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("4");
    }

    @Test
    void 입력_값이_시작_노드의_입력_항목과_맞지_않으면_접수하지_않는다() throws Exception {
        workflowId = saveWithStartAndEnd();

        mockMvc.perform(run().param("input", "{}").with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_009"))
                .andExpect(jsonPath("$.error.data.key").value("brand_name"));

        assertThat(outboxCountOf(requester)).isZero();
    }

    @Test
    void 엑셀도_CSV_도_아니면_접수하지_않는다() throws Exception {
        mockMvc.perform(run().file(excel("성적.pdf", "점수"))
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_003"));

        assertThat(outboxCountOf(requester)).isZero();
    }

    @Test
    void 워크플로우를_고르지_않으면_400_이다() throws Exception {
        mockMvc.perform(multipart("/api/v1/runs").with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    void 요청_번호가_없으면_400_이다() throws Exception {
        mockMvc.perform(multipart("/api/v1/runs").param("workflowId", workflowId)
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        assertThat(outboxCountOf(requester)).isZero();
    }

    @Test
    void 맡기자마자_물어보면_대기_상태로_답하고_엔진에_묻지_않는다() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.total").value(2));

        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    // 재기동 직후처럼 진행 저장소가 비었다. 엔진에 물어 닫지 않는다 — 이벤트가 다시 오거나 감시가 닫는다.
    @Test
    void 진행_저장소에_없는_RUNNING_실행은_DB_상태로_답하고_엔진을_부르지_않고_닫지도_않는다() throws Exception {
        String executionId = start();
        markRunning(executionId);

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.steps").isEmpty());

        then(aiRuntimeClient).shouldHaveNoInteractions();
        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.RUNNING);
        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
    }

    @Test
    void 남의_실행은_찾을_수_없다고_한다() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    @Test
    void 구독하면_지금_상태가_이벤트로_먼저_온다() throws Exception {
        String executionId = start();

        MvcResult streamed = mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(streamed.getResponse().getContentAsString())
                .contains("event:progress")
                .contains("\"status\":\"QUEUED\"");
    }

    // 끝난 실행은 기존대로다 — 엔진이 아직 들고 있으면 단계까지 보여 준다.
    @Test
    void 이미_끝난_실행을_구독하면_마지막_상태를_그대로_준다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.SUCCEEDED);
        given(aiRuntimeClient.read(anyString())).willReturn(succeeded());

        MvcResult streamed = mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(streamed.getResponse().getContentAsString()).contains("\"status\":\"SUCCEEDED\"");
    }

    @Test
    void 끝난_실행이_멎었으면_어느_단계에서_왜_멎었는지_함께_준다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.FAILED);
        given(aiRuntimeClient.read(anyString())).willReturn(failed());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.stepName").value("가정통신문 작성"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.data.failure.fault").value("supplier"));
    }

    @Test
    void 끝난_실행은_엔진이_뒤늦게_돌고_있다고_해도_되살아나지_않는다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.FAILED);
        given(aiRuntimeClient.read(anyString())).willReturn(running());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("ABC_RUNTIME_UNREPORTED"));

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.FAILED);
    }

    @Test
    void 엔진이_끝난_실행을_잊어도_우리가_남긴_것으로_답한다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.FAILED);
        willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone")).given(aiRuntimeClient).read(anyString());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("ABC_RUNTIME_UNREPORTED"));
    }

    // B54 (E2E 버그 1) — dead letter 로 닫힌 실행은 런타임이 받은 적이 없어 엔진이 늘 모른다.
    // 새로고침 · 재접속으로 다시 조회해도 닫을 때 적은 실패 판정으로 답한다.
    @Test
    void dead_letter_로_닫힌_실행을_다시_조회해도_닫을_때_적은_실패_코드와_책임으로_답한다() throws Exception {
        String executionId = start();
        markDispatched(executionId);
        executionService.closeDeadLettered(ExecutionId.of(executionId), DeadLetterReason.EXPIRED);
        progressStore.forget(ExecutionId.of(executionId));
        willThrow(new AiClientException(AiClientError.RUN_NOT_FOUND, "gone")).given(aiRuntimeClient).read(anyString());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("ABC_QUEUE_EXPIRED"))
                .andExpect(jsonPath("$.data.failure.fault").value("platform"));
    }

    @Test
    void 남의_실행은_구독할_수_없다() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(other)))
                .andExpect(status().isNotFound());
    }

    // 떼어 둔 6 은 두 단계의 단가 3 + 3 이다. n1 만 성공하고 n2 는 공급자 탓으로 실패해 n2 의 3 을 돌려받아 잔액은 7 이 된다.
    // 쓴 3 은 두 노드를 만든 실행자 자신의 정산금이 된다.
    @Test
    void failed_이벤트로_닫히면_쓰지_않은_만큼을_잔액으로_돌려준다() throws Exception {
        String executionId = start();

        executionService.applyEvent(RunEventFixtures.failed(executionId, 1, 1, Long.toString(requester)));

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .isEqualTo(ExecutionStatus.FAILED);
        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
        assertThat(balanceOf(requester, LedgerAccountType.PAYOUT_BALANCE)).isEqualByComparingTo("3");
    }

    @Test
    void 이벤트로_닫힌_뒤에_여러_번_조회해도_다시_돌려주지_않는다() throws Exception {
        String executionId = start();
        executionService.applyEvent(RunEventFixtures.failed(executionId, 1, 1, Long.toString(requester)));
        given(aiRuntimeClient.read(anyString())).willReturn(failed());

        for (int poll = 0; poll < 3; poll++) {
            mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("FAILED"));
        }

        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("7");
    }

    @Test
    void 아직_돌고_있으면_돌려주지_않는다() throws Exception {
        String executionId = start();

        executionService.applyEvent(RunEventFixtures.accepted(executionId, 1, 1));
        executionService.applyEvent(RunEventFixtures.progress(executionId, 1, 2, Long.toString(requester)));

        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("6");
        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.done").value(1))
                .andExpect(jsonPath("$.data.currentStepName").value("가정통신문 작성"));
    }

    // mock 은 모델을 부르지 않는다 (계약 8.1). 접수 때 떼고, 끝나면 받을 것이 없어 전부 돌려준다.
    @Test
    void mock_실행은_끝나면_떼어_둔_것을_전부_돌려준다() throws Exception {
        MvcResult accepted = mockMvc.perform(run().param("mock", "true").with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();
        String executionId = executionIdOf(accepted);
        String owner = Long.toString(requester);

        executionService.applyEvent(RunEventFixtures.of(executionId, RunMode.RUN, 1, 1, RunEventType.SUCCEEDED, """
                {"run_id":"%s","status":"succeeded","mock":true,"progress":{"done":2,"total":2,"percent":100},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s","cost_usd":0},
                          {"id":"n2","name":"가정통신문 작성","type":"llm","status":"succeeded","owner_id":"%s","cost_usd":0}]}"""
                .formatted(executionId, owner, owner), null));

        assertThat(balanceOf(requester, LedgerAccountType.ABC_BALANCE)).isEqualByComparingTo("10");
        assertThat(balanceOf(requester, LedgerAccountType.EXECUTION_ESCROW)).isEqualByComparingTo("0");
    }

    @Test
    void succeeded_로_닫히면_결과_볼륨의_결과물을_보관해_파일로_내려준다() throws Exception {
        String executionId = finishedWithResults(true);

        assertThat(FILE_ROOT.resolve("executions/" + executionId + "/result.json")).exists().hasContent(ARTIFACT);

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + executionId + ".json\""))
                .andExpect(content().string(ARTIFACT));
        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void PDF_로_달라고_하면_PDF_바이트를_그대로_내려준다() throws Exception {
        String executionId = finishedWithResults(true);

        MvcResult received = mockMvc.perform(get("/api/v1/runs/{id}/result", executionId)
                        .param("format", "PDF").with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + executionId + ".pdf\""))
                .andReturn();

        assertThat(received.getResponse().getContentAsByteArray()).isEqualTo(PDF);
    }

    @Test
    void PDF_를_만들지_않은_실행에_PDF_를_달라고_하면_엔진에_묻지_않고_404_다() throws Exception {
        String executionId = finishedWithResults(false);

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId)
                        .param("format", "PDF").with(as(requester)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_007"));

        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    // 닫을 때 보관하지 못했다(볼륨이 늦게 붙었다 · 서버가 그 사이 내려갔다). 받으러 올 때 결과 볼륨에서 다시 가져온다.
    @Test
    void 보관한_결과가_없으면_받으러_올_때_결과_볼륨에서_다시_가져온다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.SUCCEEDED);
        writeResult(executionId, "artifact.json", ARTIFACT.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().string(ARTIFACT));

        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void 결과_볼륨에도_없으면_404_다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.SUCCEEDED);

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_007"));

        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void 아직_끝나지_않은_실행의_결과는_409_다() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    @Test
    void 실패로_끝난_실행의_결과는_404_다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.FAILED);

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_008"));
    }

    @Test
    void 남의_실행_결과는_찾을_수_없다고_한다() throws Exception {
        String executionId = start();
        markFinished(executionId, ExecutionStatus.SUCCEEDED);

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    private String finishedWithResults(boolean withPdf) throws Exception {
        String executionId = start();
        writeResult(executionId, "artifact.json", ARTIFACT.getBytes(StandardCharsets.UTF_8));

        if (withPdf) {
            writeResult(executionId, "report.pdf", PDF);
        }

        RunEvent succeeded = RunEventFixtures.succeeded(executionId, 1, 1, Long.toString(requester));
        executionService.applyEvent(succeeded);

        return executionId;
    }

    private String saveWithStartAndEnd() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "브랜드 분석",
                                  "nodes": [
                                    { "nodeKey": "s1", "sourceType": "IO", "ioType": "START",
                                      "fields": [ { "key": "brand_name", "label": "브랜드명", "type": "TEXT",
                                                    "required": true } ] },
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "브랜드 분석", "prompt": "브랜드를 분석한다" },
                                    { "nodeKey": "e1", "sourceType": "IO", "ioType": "END",
                                      "resultShape": "DOCUMENT", "resultFormat": "PDF" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "g1", "sourceNodeKey": "s1", "targetNodeKey": "n1" },
                                    { "edgeKey": "g2", "sourceNodeKey": "n1", "targetNodeKey": "e1" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(saved.getResponse().getContentAsString()).get("data").get("workflowId").asString();
    }

    private MockMultipartHttpServletRequestBuilder run() {
        return run(UUID.randomUUID().toString());
    }

    private MockMultipartHttpServletRequestBuilder run(String requestId) {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/runs")
                .param("workflowId", workflowId)
                .param("requestId", requestId);
    }

    private String start() throws Exception {
        MvcResult accepted = mockMvc.perform(run().with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.stepCount").value(2))
                .andReturn();

        return executionIdOf(accepted);
    }

    private String startWith(String requestId) throws Exception {
        MvcResult accepted = mockMvc.perform(run(requestId).with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();

        return executionIdOf(accepted);
    }

    private String executionIdOf(MvcResult accepted) throws Exception {
        return mapper.readTree(accepted.getResponse().getContentAsString()).get("data").get("executionId").asString();
    }

    private JsonNode submitOf(String executionId) {
        return mapper.readTree(executionOutboxRepository.findById(UUID.fromString(executionId)).orElseThrow()
                .getPayload());
    }

    private long outboxCountOf(long userId) {
        return executionRepository.findAll().stream()
                .filter(execution -> execution.getRequesterId() == userId)
                .filter(execution -> executionOutboxRepository.existsById(execution.getId()))
                .count();
    }

    // 넘긴 뒤 accepted 를 받아 RUNNING 인 실행을 만든다. 재기동 뒤처럼 진행 저장소에는 없다.
    private void markRunning(String executionId) {
        ExecutionEntity execution = executionRepository.findById(UUID.fromString(executionId)).orElseThrow();
        execution.markDispatched(executionId);
        execution.moveTo(ExecutionStatus.RUNNING);
        executionRepository.flush();
        progressStore.forget(ExecutionId.of(executionId));
    }

    // 브로커가 confirm 해 넘긴 실행이다. 런타임은 아직 받지 않아 QUEUED 다.
    private void markDispatched(String executionId) {
        executionRepository.findById(UUID.fromString(executionId)).orElseThrow().markDispatched(executionId);
        executionRepository.flush();
    }

    // 이벤트로 닫힌 것처럼 만든다. 닫히면 따라가던 기억도 지운다.
    private void markFinished(String executionId, ExecutionStatus status) {
        ExecutionEntity execution = executionRepository.findById(UUID.fromString(executionId)).orElseThrow();
        execution.markDispatched(executionId);
        execution.moveTo(status);
        executionRepository.flush();
        progressStore.forget(ExecutionId.of(executionId));
    }

    private void writeResult(String executionId, String filename, byte[] content) throws IOException {
        Path path = RESULTS_ROOT.resolve(executionId).resolve(filename);

        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void charge(long userId, String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.save(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, Long.toString(userId)));

        ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now());
    }

    private BigDecimal balanceOf(long userId, LedgerAccountType accountType) {
        return ledgerAccountRepository.findByAccountTypeAndReferenceId(accountType, Long.toString(userId))
                .map(LedgerAccountEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    private static MockMultipartFile excel(String filename, String content) {
        return new MockMultipartFile("file", filename,
                MediaType.APPLICATION_OCTET_STREAM_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }

    private static RunStatus succeeded() {
        return new RunStatus("run", "succeeded",
                new RunStatus.Progress(2, 2, 100, null, null),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, null, null, null),
                        new RunStatus.Node("n2", "가정통신문 작성", "llm", "succeeded", null, null, null, null)),
                List.of(), List.of(), null, null, false, null);
    }

    private static RunStatus running() {
        return new RunStatus("run", "running",
                new RunStatus.Progress(1, 2, 50, "n2", "가정통신문 작성"),
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    private static RunStatus failed() {
        return new RunStatus("run", "failed",
                new RunStatus.Progress(1, 2, 50, "n2", "가정통신문 작성"),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, null, null, null),
                        new RunStatus.Node("n2", "가정통신문 작성", "llm", "failed", null, null, null, null)),
                List.of(), List.of(), null,
                new RunStatus.Failure("n2", "MODEL_TIMEOUT", "supplier", true), false, null);
    }

    private static Path createTempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void clear(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(root))
                    .forEach(path -> path.toFile().delete());
        }
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
