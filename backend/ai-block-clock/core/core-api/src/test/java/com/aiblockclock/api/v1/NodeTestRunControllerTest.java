package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunMode;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 새 노드의 원가는 기본값 0.018 로 시작한다. 잰 원가가 있으면 최근 다섯 번의 중앙값 × 1.5 다.
@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NodeTestRunControllerTest {
    private static final String ARTIFACT = """
            {"a2a_version":"2.0","node_id":"n1","schema_id":"text","payload":{"text":"이번 학기 평균이 올랐습니다"}}""";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeTestRunRepository nodeTestRunRepository;

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
        given(aiRuntimeClient.start(any())).willReturn(accepted("run-1"));
    }

    @Test
    void 테스트를_맡기면_202_와_RUNNING_이_오고_노드_하나를_test_모드로_보낸다() throws Exception {
        mockMvc.perform(testRun(owner, "{\"comment\":\"2학기 점수를 비교해 주세요\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.nodeId").value(nodeId))
                .andExpect(jsonPath("$.data.mock").value(false))
                .andExpect(jsonPath("$.data.baseCost").value("0.018000"))
                .andExpect(jsonPath("$.data.costUsd").isEmpty());

        ArgumentCaptor<RunRequest> sent = ArgumentCaptor.captor();
        then(aiRuntimeClient).should().start(sent.capture());
        JsonNode workflow = mapper.readTree(sent.getValue().workflow());

        assertThat(sent.getValue().mode()).isEqualTo(RunMode.TEST);
        assertThat(sent.getValue().comment()).isEqualTo("2학기 점수를 비교해 주세요");
        assertThat(workflow.get("nodes")).hasSize(1);
        assertThat(workflow.get("nodes").get(0).get("prompt").asText()).isEqualTo("당신은 학원 성적 데이터 분석가입니다.");
        assertThat(workflow.get("nodes").get(0).get("output").get("kind").asText()).isEqualTo("text");
        assertThat(workflow.get("edges")).isEmpty();
    }

    @Test
    void 본문_없이_맡겨도_된다() throws Exception {
        mockMvc.perform(post("/api/v1/nodes/{nodeId}/test-runs", nodeId).with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    // 잰 0.004 달러 × 1.5 × 1 ABC/달러 = 0.006 ABC
    @Test
    void 끝나면_결과물과_엔진이_잰_원가로_새로_정한_노드_원가가_온다() throws Exception {
        String testRunId = start(owner);
        given(aiRuntimeClient.read("run-1")).willReturn(succeeded("0.004"));
        given(aiRuntimeClient.readArtifact("run-1")).willReturn(ARTIFACT);

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.costUsd").value("0.004000"))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"))
                .andExpect(jsonPath("$.data.result.payload.text").value("이번 학기 평균이 올랐습니다"))
                .andExpect(jsonPath("$.data.finishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"));
    }

    @Test
    void 아직_도는_중이면_RUNNING_그대로_온다() throws Exception {
        String testRunId = start(owner);
        given(aiRuntimeClient.read("run-1")).willReturn(running());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.result").isEmpty());
    }

    @Test
    void 엔진이_잠시_답하지_못하면_RUNNING_그대로_답한다() throws Exception {
        String testRunId = start(owner);
        given(aiRuntimeClient.read("run-1")).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
    }

    // 셋의 중앙값 0.004 × 1.5 = 0.006. 긴 입력으로 한 번 돌린 0.05 에 끌려가지 않는다.
    @Test
    void 원가를_잰_테스트가_여럿이면_중앙값으로_정한다() throws Exception {
        finishWith("run-1", "0.002");
        finishWith("run-2", "0.05");
        finishWith("run-3", "0.004");

        mockMvc.perform(get("/api/v1/nodes/{nodeId}", nodeId).with(as(owner)))
                .andExpect(jsonPath("$.data.baseCost").value("0.006000"));
    }

    @Test
    void 같은_끝을_여러_번_조회해도_한_번만_닫는다() throws Exception {
        String testRunId = start(owner);
        given(aiRuntimeClient.read("run-1")).willReturn(succeeded("0.004"));

        for (int poll = 0; poll < 3; poll++) {
            mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                    .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
        }

        then(aiRuntimeClient).should(org.mockito.Mockito.times(1)).read("run-1");
    }

    @Test
    void mock_테스트는_결과물이_가짜라_원가를_바꾸지_않는다() throws Exception {
        String testRunId = start(owner, "{\"mock\":true}");
        given(aiRuntimeClient.read("run-1")).willReturn(succeeded("0.0"));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.mock").value(true))
                .andExpect(jsonPath("$.data.baseCost").value("0.018000"));
    }

    @Test
    void 실패한_테스트는_원가를_바꾸지_않고_실패한_까닭을_남긴다() throws Exception {
        String testRunId = start(owner);
        given(aiRuntimeClient.read("run-1")).willReturn(failed());

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureCode").value("MODEL_TIMEOUT"))
                .andExpect(jsonPath("$.data.baseCost").value("0.018000"))
                .andExpect(jsonPath("$.data.result").isEmpty());
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
    void 엔진이_받지_않으면_502_이고_하루_횟수를_쓰지_않는다() throws Exception {
        given(aiRuntimeClient.start(any())).willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "down"));

        mockMvc.perform(testRun(owner, "{}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_002"));

        assertThat(nodeTestRunRepository.count()).isZero();
    }

    @Test
    void 남의_노드는_테스트할_수_없다() throws Exception {
        mockMvc.perform(testRun(other, "{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NODE_002"));

        then(aiRuntimeClient).should(org.mockito.Mockito.never()).start(any());
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
        finishWith("run-1", "0.004");

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

    private void finishWith(String runId, String costUsd) throws Exception {
        given(aiRuntimeClient.start(any())).willReturn(accepted(runId));
        String testRunId = start(owner);
        given(aiRuntimeClient.read(runId)).willReturn(succeeded(costUsd));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/test-runs/{id}", nodeId, testRunId).with(as(owner)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    private String start(long requester) throws Exception {
        return start(requester, "{}");
    }

    private String start(long requester, String body) throws Exception {
        MvcResult result = mockMvc.perform(testRun(requester, body))
                .andExpect(status().isAccepted())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("testRunId").asText();
    }

    private org.springframework.test.web.servlet.RequestBuilder testRun(long requester, String body) {
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

        return mapper.readTree(result.getResponse().getContentAsString()).get("data").get("nodeId").asText();
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(UserId.of(userId), null, List.of()));
    }

    private static RunAccepted accepted(String runId) {
        return new RunAccepted(runId, "queued", 1, 2000);
    }

    private RunStatus succeeded(String costUsd) {
        BigDecimal cost = new BigDecimal(costUsd);

        return new RunStatus("run", "succeeded", new RunStatus.Progress(1, 1, 100, null, null),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, Long.toString(owner), cost, null)),
                List.of(), List.of(), null, null, cost.signum() == 0, new RunStatus.Totals(cost));
    }

    private static RunStatus running() {
        return new RunStatus("run", "running", new RunStatus.Progress(0, 1, 0, "n1", "성적 분석"),
                List.of(), List.of(), List.of(), null, null, false, null);
    }

    private RunStatus failed() {
        return new RunStatus("run", "failed", new RunStatus.Progress(0, 1, 0, "n1", "성적 분석"),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "failed", null, Long.toString(owner),
                        new BigDecimal("0.001"), null)),
                List.of(), List.of(), null, new RunStatus.Failure("n1", "MODEL_TIMEOUT", "supplier", true), false,
                new RunStatus.Totals(new BigDecimal("0.001")));
    }
}
