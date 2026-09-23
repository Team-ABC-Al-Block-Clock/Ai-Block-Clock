package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionProgressStore;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "execution.polling.interval=1h")
class ExecutionControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionProgressStore progressStore;

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    private long requester;

    private long other;

    private String workflowId;

    @BeforeEach
    void 워크플로우를_하나_저장해_둔다() throws Exception {
        requester = createUser();
        other = createUser();
        given(aiRuntimeClient.start(any())).willReturn(new RunAccepted("20260919-1", "queued", 2, 2000));

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
                .get("data").get("workflowId").asText();
    }

    @Test
    @DisplayName("실행을 맡기면 202 와 실행 ID 가 나오고 접수 번호가 남는다")
    void acceptsRunAndRemembersTheRunId() throws Exception {
        String executionId = start();

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow())
                .satisfies(execution -> {
                    assertThat(execution.getRunId()).isEqualTo("20260919-1");
                    assertThat(execution.getPromptCount()).isEqualTo(2);
                });
    }

    @Test
    @DisplayName("펼친 프롬프트를 실행 엔진이 읽는 모양으로 보낸다")
    void sendsFlattenedPromptsAsRuntimeWorkflow() throws Exception {
        start();

        ArgumentCaptor<RunRequest> sent = ArgumentCaptor.captor();
        then(aiRuntimeClient).should().start(sent.capture());

        assertThat(sent.getValue().workflow())
                .contains("\"type\":\"llm\"")
                .contains("점수를 비교한다")
                .contains("\"from\":\"n1\"")
                .contains("output_schema_id");
        assertThat(sent.getValue().mock()).isFalse();
    }

    @Test
    @DisplayName("맡기자마자 물어보면 뒤에서 알아낸 것을 준다 — 엔진을 다시 두드리지 않는다")
    void answersFromWhatTheBackgroundPollKnows() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.total").value(2));

        then(aiRuntimeClient).should(never()).read(anyString());
    }

    @Test
    @DisplayName("따라가던 기억이 없으면 엔진에 직접 묻고 끝난 상태를 우리 쪽에도 남긴다")
    void asksTheEngineWhenNothingIsRemembered() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(succeeded());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.percent").value(100))
                .andExpect(jsonPath("$.data.steps[0].name").value("성적 분석"));

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .hasToString("SUCCEEDED");
    }

    @Test
    @DisplayName("구독하면 지금 상태가 이벤트로 먼저 온다")
    void sendsTheCurrentStateAsSoonAsSomeoneSubscribes() throws Exception {
        String executionId = start();

        MvcResult streamed = mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(streamed.getResponse().getContentAsString())
                .contains("event:progress")
                .contains("\"status\":\"QUEUED\"");
    }

    @Test
    @DisplayName("이미 끝난 실행을 구독하면 마지막 상태를 그대로 준다")
    void sendsTheFinalStateToALateSubscriber() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(succeeded());

        MvcResult streamed = mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(streamed.getResponse().getContentAsString()).contains("\"status\":\"SUCCEEDED\"");
    }

    @Test
    @DisplayName("남의 실행은 구독할 수 없다")
    void hidesSomeoneElsesStream() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}/stream", executionId).with(as(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("멎으면 어느 단계에서 왜 멎었는지 함께 준다")
    void relaysWhyTheRunStopped() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(failed());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.stepName").value("가정통신문 작성"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("MODEL_API_ERROR"))
                .andExpect(jsonPath("$.data.failure.fault").value("supplier"));
    }

    @Test
    @DisplayName("잘 끝난 실행에는 실패 칸이 비어 있다")
    void leavesFailureEmptyWhenTheRunSucceeds() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(succeeded());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failure").doesNotExist());
    }

    @Test
    @DisplayName("끝난 실행은 엔진이 뒤늦게 돌고 있다고 해도 되살아나지 않는다")
    void neverResurrectsAConcludedRun() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(failed());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        given(aiRuntimeClient.read(anyString())).willReturn(running());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("ABC_RUNTIME_UNREPORTED"));

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow().getStatus())
                .hasToString("FAILED");
    }

    @Test
    @DisplayName("엔진이 끝난 실행을 잊어도 우리가 남긴 것으로 답한다")
    void answersFromOurOwnRecordWhenTheEngineForgot() throws Exception {
        String executionId = forgotten();
        given(aiRuntimeClient.read(anyString())).willReturn(failed());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"));

        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "run not found"))
                .given(aiRuntimeClient).read(anyString());

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failure.errorCode").value("ABC_RUNTIME_UNREPORTED"));
    }

    @Test
    @DisplayName("남의 실행은 찾을 수 없다고 한다")
    void hidesSomeoneElsesExecution() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    @Test
    @DisplayName("실행 엔진에 맡기지 못하면 502 다")
    void failsWhenTheEngineIsUnreachable() throws Exception {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"))
                .given(aiRuntimeClient).start(any());

        mockMvc.perform(post("/api/v1/runs")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"%s\"}".formatted(workflowId)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_002"));
    }

    private String start() throws Exception {
        MvcResult accepted = mockMvc.perform(post("/api/v1/runs")
                        .with(as(requester)).with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"%s\"}".formatted(workflowId)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.stepCount").value(2))
                .andReturn();

        return mapper.readTree(accepted.getResponse().getContentAsString())
                .get("data").get("executionId").asText();
    }

    private static RunStatus succeeded() {
        return new RunStatus("20260919-1", "succeeded",
                new RunStatus.Progress(2, 2, 100, null, null),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, null, null, null),
                        new RunStatus.Node("n2", "가정통신문 작성", "llm", "succeeded", null, null, null, null)),
                List.of(), List.of(), null, null);
    }

    private String forgotten() throws Exception {
        String executionId = start();

        progressStore.forget(ExecutionId.of(executionId));

        return executionId;
    }

    private static RunStatus running() {
        return new RunStatus("20260919-1", "running",
                new RunStatus.Progress(1, 2, 50, "n2", "가정통신문 작성"),
                List.of(), List.of(), List.of(), null, null);
    }

    private static RunStatus failed() {
        return new RunStatus("20260919-1", "failed",
                new RunStatus.Progress(1, 2, 50, "n2", "가정통신문 작성"),
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, null, null, null),
                        new RunStatus.Node("n2", "가정통신문 작성", "llm", "failed", null, null, null, null)),
                List.of(), List.of(), null,
                new RunStatus.Failure("n2", "MODEL_API_ERROR", "supplier"));
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
