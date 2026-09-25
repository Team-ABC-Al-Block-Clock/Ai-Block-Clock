package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionProgressStore;
import com.aiblockclock.domain.execution.ExecutionService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "execution.polling.interval=1h")
class ExecutionControllerTest {
    private static final Path FILE_ROOT = createTempDirectory();
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
    private ExecutionProgressStore progressStore;

    @Autowired
    private ExecutionService executionService;

    @MockitoBean
    private AiRuntimeClient aiRuntimeClient;

    private long requester;

    private long other;

    private String workflowId;

    @DynamicPropertySource
    static void 파일은_임시_폴더에_쌓는다(DynamicPropertyRegistry registry) {
        registry.add("file-store.root", () -> FILE_ROOT);
    }

    @BeforeEach
    void 워크플로우를_하나_저장해_둔다() throws Exception {
        clearFileRoot();
        requester = createUser();
        other = createUser();
        given(aiRuntimeClient.start(any())).willReturn(new RunAccepted("20260919-1", "queued", 2, 2000));
        given(aiRuntimeClient.readArtifact(anyString())).willReturn(ARTIFACT);
        given(aiRuntimeClient.readPdf(anyString())).willReturn(Optional.of(PDF));

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

        mockMvc.perform(run().with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_002"));
    }

    @Test
    @DisplayName("올린 파일을 보관하고 실행 엔진에 함께 실어 보낸다")
    void storesTheUploadedFileAndSendsItAlong() throws Exception {
        MvcResult accepted = mockMvc.perform(run().file(excel("2학기 성적.xlsx", "점수"))
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isAccepted())
                .andReturn();

        String executionId = mapper.readTree(accepted.getResponse().getContentAsString())
                .get("data").get("executionId").asText();

        assertThat(executionRepository.findById(UUID.fromString(executionId)).orElseThrow())
                .satisfies(execution -> {
                            assertThat(execution.getInputFileKey()).isEqualTo("executions/" + executionId + "/input.xlsx");
                    assertThat(execution.getInputFileName()).isEqualTo("2학기 성적.xlsx");
                    assertThat(execution.getInputFileSize()).isEqualTo(6);
                });

        ArgumentCaptor<RunRequest> sent = ArgumentCaptor.captor();
        then(aiRuntimeClient).should().start(sent.capture());

        assertThat(sent.getValue().file()).isNotNull()
                .satisfies(path -> assertThat(Files.readString(path)).isEqualTo("점수"));
    }

    @Test
    @DisplayName("엑셀도 CSV 도 아니면 실행 엔진을 부르지 않는다")
    void refusesAFileTheEngineWouldReject() throws Exception {
        mockMvc.perform(run().file(excel("성적.pdf", "점수"))
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_003"));

        then(aiRuntimeClient).should(never()).start(any());
    }

    @Test
    @DisplayName("접수에 실패하면 저장한 파일을 남기지 않는다")
    void erasesTheStoredFileWhenTheEngineRefuses() throws Exception {
        willThrow(new AiClientException(AiClientError.REQUEST_FAILED, "engine is down"))
                .given(aiRuntimeClient).start(any());

        mockMvc.perform(run().file(excel("성적.csv", "점수"))
                        .with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadGateway());

        assertThat(FILE_ROOT.resolve("executions")).isEmptyDirectory();
    }

    @Test
    @DisplayName("워크플로우를 고르지 않으면 400 이다")
    void refusesARequestWithoutAWorkflow() throws Exception {
        mockMvc.perform(multipart("/api/v1/runs").with(as(requester)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("파일 없이도 실행한다")
    void startsWithoutAFile() throws Exception {
        start();

        ArgumentCaptor<RunRequest> sent = ArgumentCaptor.captor();
        then(aiRuntimeClient).should().start(sent.capture());

        assertThat(sent.getValue().file()).isNull();
    }

    private MockMultipartHttpServletRequestBuilder run() {
        return (MockMultipartHttpServletRequestBuilder) multipart("/api/v1/runs")
                .param("workflowId", workflowId);
    }

    private static MockMultipartFile excel(String filename, String content) {
        return new MockMultipartFile("file", filename,
                MediaType.APPLICATION_OCTET_STREAM_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("끝난 실행의 결과를 로컬에 쌓아 두고 파일로 내려준다")
    void storesResultsOnDiskAndServesThem() throws Exception {
        String executionId = finish();

        assertThat(FILE_ROOT.resolve("executions/" + executionId + "/result.json"))
                .exists().hasContent(ARTIFACT);
        assertThat(FILE_ROOT.resolve("executions/" + executionId + "/result.pdf")).exists();

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"" + executionId + ".json\""))
                .andExpect(content().string(ARTIFACT));
    }

    @Test
    @DisplayName("PDF 로 달라고 하면 PDF 바이트를 그대로 내려준다")
    void servesThePdf() throws Exception {
        String executionId = finish();

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
    @DisplayName("아직 끝나지 않은 실행의 결과는 409 다")
    void refusesResultBeforeTheRunFinishes() throws Exception {
        String executionId = start();

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(requester)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    @Test
    @DisplayName("렌더되지 않은 형식을 달라고 하면 404 다")
    void refusesFormatThatWasNeverRendered() throws Exception {
        given(aiRuntimeClient.readPdf(anyString())).willReturn(Optional.empty());
        String executionId = finish();

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId)
                        .param("format", "PDF").with(as(requester)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    @Test
    @DisplayName("남의 실행 결과는 찾을 수 없다고 한다")
    void hidesSomeoneElsesResult() throws Exception {
        String executionId = finish();

        mockMvc.perform(get("/api/v1/runs/{id}/result", executionId).with(as(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("EXECUTION_001"));
    }

    private String finish() throws Exception {
        String executionId = start();
        given(aiRuntimeClient.read(anyString())).willReturn(succeeded());

        executionService.pollOnce(ExecutionId.of(executionId));

        mockMvc.perform(get("/api/v1/runs/{id}", executionId).with(as(requester)))
                .andExpect(status().isOk());

        return executionId;
    }

    private String start() throws Exception {
        MvcResult accepted = mockMvc.perform(run().with(as(requester)).with(csrf().asHeader()))
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

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("execution-files");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void clearFileRoot() throws IOException {
        try (Stream<Path> paths = Files.walk(FILE_ROOT)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(FILE_ROOT))
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
