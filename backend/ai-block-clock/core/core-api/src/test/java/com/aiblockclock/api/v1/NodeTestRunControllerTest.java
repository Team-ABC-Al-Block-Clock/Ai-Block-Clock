package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunEvent;
import com.aiblockclock.client.ai.RunEventType;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunQueuePublisher;
import com.aiblockclock.client.ai.SubmitRun;
import com.aiblockclock.domain.execution.DeadLetterReason;
import com.aiblockclock.domain.execution.NodeTestRunService;
import com.aiblockclock.domain.execution.RunEventFixtures;
import com.aiblockclock.domain.node.NodeTestRunId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeTestRunRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 노드 테스트는 브로커로 맡기고(confirm 까지 기다린다) 이벤트로 닫는다(ADR-0001). 엔진 HTTP 로 맡기지 않는다.
// 새 노드의 원가는 기본값 0.0135 로 시작한다. 잰 원가가 있으면 최근 다섯 번의 중앙값 × 1.5 다.
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NodeTestRunControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeTestRunRepository nodeTestRunRepository;

    @Autowired
    private NodeTestRunService nodeTestRunService;

    @MockitoBean
    private RunQueuePublisher runQueuePublisher;

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    private long owner;

    private long other;

    private String nodeId;

    @BeforeEach
    void 주인과_남과_노드_하나를_준비한다() throws Exception {
        owner = createUser();
        other = createUser();
        nodeId = createNode();
    }

    @Test
    void 테스트를_맡기면_202_와_RUNNING_이_오고_노드_하나를_test_모드로_발행한다() throws Exception {
        MvcResult accepted = mockMvc.perform(testRun(owner, "{\"comment\":\"2학기 점수를 비교해 주세요\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.nodeId").value(nodeId))
                .andExpect(jsonPath("$.data.mock").value(false))
                .andExpect(jsonPath("$.data.baseCost").value("0.013500"))
                .andExpect(jsonPath("$.data.costUsd").isEmpty())
                .andReturn();
        String testRunId = testRunIdOf(accepted);

        ArgumentCaptor<SubmitRun> sent = ArgumentCaptor.forClass(SubmitRun.class);
        then(runQueuePublisher).should().submit(sent.capture());
        JsonNode workflow = sent.getValue().workflow();

        assertThat(sent.getValue().executionId()).isEqualTo(testRunId);
        assertThat(sent.getValue().mode()).isEqualTo(RunMode.TEST);
        assertThat(sent.getValue().comment()).isEqualTo("2학기 점수를 비교해 주세요");
        assertThat(sent.getValue().files()).isEmpty();
        assertThat(workflow.get("nodes")).hasSize(1);
        assertThat(workflow.get("nodes").get(0).get("prompt").asString()).isEqualTo("당신은 학원 성적 데이터 분석가입니다.");
        assertThat(workflow.get("nodes").get(0).get("output").get("kind").asString()).isEqualTo("text");
        assertThat(workflow.get("model").asString()).isEqualTo("gemini-2.5-flash");
        assertThat(workflow.get("edges")).isEmpty();
        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void 발행이_confirm_을_받으면_테스트_실행_번호를_런타임_실행_번호로_남긴다() throws Exception {
        String testRunId = start(owner);

        assertThat(nodeTestRunRepository.findById(UUID.fromString(testRunId)).orElseThrow().getRunId())
                .isEqualTo(testRunId);
    }

    @Test
    void 본문_없이_맡겨도_된다() throws Exception {
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/test-runs", nodeId).with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    @Test
    void 발행하지_못하면_502_이고_하루_횟수를_쓰지_않는다() throws Exception {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "nack")).given(runQueuePublisher).submit(any());

        mockMvc.perform(testRun(owner, "{}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_002"));

        assertThat(nodeTestRunRepository.count()).isZero();
    }

    // 잰 0.004 달러 × 1.5 × 1 ABC/달러 = 0.006 ABC
    @Test
    void succeeded_이벤트가_오면_결과물과_잰_원가로_새로_정한_노드_원가가_온다() throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.applyEvent(succeeded(testRunId, "0.004"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.costUsd").value("0.004000"))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"))
                .andExpect(jsonPath("$.data.result.payload.text").value("이번 학기 평균이 올랐습니다"))
                .andExpect(jsonPath("$.data.finishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"));
        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void 아직_도는_중이면_엔진에_묻지_않고_RUNNING_그대로_온다() throws Exception {
        String testRunId = start(owner);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.result").isEmpty());

        then(aiRuntimeClient).shouldHaveNoInteractions();
    }

    @Test
    void progress_이벤트는_테스트를_바꾸지_않는다() throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.applyEvent(RunEventFixtures.of(testRunId, RunMode.TEST, 1, 2, RunEventType.PROGRESS, """
                {"run_id":"%s","status":"running","progress":{"done":0,"total":1,"percent":0},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"running"}]}""".formatted(testRunId), null));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    // 셋의 중앙값 0.004 × 1.5 = 0.006. 긴 입력으로 한 번 돌린 0.05 에 끌려가지 않는다.
    @Test
    void 원가를_잰_테스트가_여럿이면_중앙값으로_정한다() throws Exception {
        finishWith("0.002");
        finishWith("0.05");
        finishWith("0.004");

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"));
    }

    @Test
    void 같은_끝을_두_번_받아도_한_번만_닫는다() throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.applyEvent(succeeded(testRunId, "0.004"));
        nodeTestRunService.applyEvent(succeeded(testRunId, "0.05"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.costUsd").value("0.004000"));
    }

    @Test
    void 이미_닫힌_테스트에_늦게_온_실패는_아무것도_바꾸지_않는다() throws Exception {
        String testRunId = start(owner);
        nodeTestRunService.applyEvent(succeeded(testRunId, "0.004"));

        nodeTestRunService.applyEvent(RunEventFixtures.of(testRunId, RunMode.TEST, 2, 1, RunEventType.FAILED,
                failedSnapshot(testRunId), null));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.failureCode").isEmpty())
                .andExpect(jsonPath("$.data.result.payload.text").value("이번 학기 평균이 올랐습니다"));
    }

    @Test
    void mock_테스트는_결과물이_가짜라_원가를_바꾸지_않는다() throws Exception {
        String testRunId = start(owner, "{\"mock\":true}");

        nodeTestRunService.applyEvent(succeeded(testRunId, "0.0"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.mock").value(true))
                .andExpect(jsonPath("$.data.baseCost").value("0.013500"));
    }

    @Test
    void 실패한_테스트는_원가를_바꾸지_않고_실패한_까닭을_남긴다() throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.applyEvent(RunEventFixtures.of(testRunId, RunMode.TEST, 1, 2, RunEventType.FAILED,
                failedSnapshot(testRunId), null));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.data.baseCost").value("0.013500"))
                .andExpect(jsonPath("$.data.result").isEmpty());
    }

    @Test
    void 큐에서_만료된_테스트는_ABC_QUEUE_EXPIRED_로_실패한다() throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.closeDeadLettered(NodeTestRunId.of(UUID.fromString(testRunId)), DeadLetterReason.EXPIRED);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("ABC_QUEUE_EXPIRED"));
    }

    @Test
    void 이미_닫힌_테스트의_dead_letter_는_그대로_둔다() throws Exception {
        String testRunId = start(owner);
        nodeTestRunService.applyEvent(succeeded(testRunId, "0.004"));

        nodeTestRunService.closeDeadLettered(NodeTestRunId.of(UUID.fromString(testRunId)),
                DeadLetterReason.DELIVERY_LIMIT);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.failureCode").isEmpty());
    }

    @Test
    void 한_노드는_하루_다섯_번까지만_테스트한다() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(testRun(owner, "{}")).andExpect(status().isAccepted());
        }

        mockMvc.perform(testRun(owner, "{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("NODE_009"));
    }

    @Test
    void 남의_노드는_테스트할_수_없다() throws Exception {
        mockMvc.perform(testRun(other, "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NODE_002"));

        then(runQueuePublisher).shouldHaveNoInteractions();
    }

    @Test
    void 남의_테스트는_있는지도_알리지_않는다() throws Exception {
        String testRunId = start(owner);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NODE_008"));
    }

    @Test
    void 고친_노드는_그동안_잰_원가를_이어_쓴다() throws Exception {
        finishWith("0.004");

        mockMvc.perform(put("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"성적 분석\", \"prompt\": \"고친 프롬프트\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"));
    }

    @Test
    void 노드를_지우면_테스트_기록도_지운다() throws Exception {
        start(owner);

        mockMvc.perform(delete("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isOk());

        assertThat(nodeTestRunRepository.count()).isZero();
    }

    private void finishWith(String costUsd) throws Exception {
        String testRunId = start(owner);

        nodeTestRunService.applyEvent(succeeded(testRunId, costUsd));
    }

    private RunEvent succeeded(String testRunId, String costUsd) {
        return RunEventFixtures.of(testRunId, RunMode.TEST, 1, 3, RunEventType.SUCCEEDED, """
                {"run_id":"%s","status":"succeeded","mock":%s,"progress":{"done":1,"total":1,"percent":100},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"succeeded","owner_id":"%s",
                           "cost_usd":%s}],
                 "totals":{"cost_usd":%s}}"""
                .formatted(testRunId, Double.parseDouble(costUsd) == 0, owner, costUsd, costUsd),
                RunEventFixtures.ARTIFACT);
    }

    private String failedSnapshot(String testRunId) {
        return """
                {"run_id":"%s","status":"failed","progress":{"done":0,"total":1,"percent":0},
                 "nodes":[{"id":"n1","name":"성적 분석","type":"llm","status":"failed","owner_id":"%s","cost_usd":0.001}],
                 "failure":{"node_id":"n1","error_code":"MODEL_TIMEOUT","fault":"supplier","affects_success_rate":true},
                 "totals":{"cost_usd":0.001}}""".formatted(testRunId, owner);
    }

    private String start(long requester) throws Exception {
        return start(requester, "{}");
    }

    private String start(long requester, String body) throws Exception {
        MvcResult result = mockMvc.perform(testRun(requester, body))
                .andExpect(status().isAccepted())
                .andReturn();

        return testRunIdOf(result);
    }

    private String testRunIdOf(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("testRunId").asString();
    }

    private RequestBuilder testRun(long requester, String body) {
        return post("/api/v1/nodes/{nodeId}/test-runs", nodeId)
                .with(as(requester))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String createNode() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"성적 분석\", \"prompt\": \"당신은 학원 성적 데이터 분석가입니다.\" }"))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("nodeId").asString();
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(UserId.of(userId), null, List.of()));
    }
}
