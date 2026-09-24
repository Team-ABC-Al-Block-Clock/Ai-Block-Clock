package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.AiClientError;
import com.aiblockclock.client.ai.AiClientException;
import com.aiblockclock.client.ai.AiRuntimeClient;
import com.aiblockclock.client.ai.RunAccepted;
import com.aiblockclock.client.ai.RunRequest;
import com.aiblockclock.client.ai.RunStatus;
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
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExecutionControllerTest {
    private static final Path FILE_ROOT = createTempDirectory();

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExecutionRepository executionRepository;

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
    @DisplayName("정산이 귀속될 주인을 노드마다 실어 보낸다")
    void sendsNodeOwnerForSettlement() throws Exception {
        start();

        ArgumentCaptor<RunRequest> sent = ArgumentCaptor.captor();
        then(aiRuntimeClient).should().start(sent.capture());

        assertThat(sent.getValue().workflow())
                .contains("\"owner_id\":\"" + requester + "\"");
    }

    @Test
    @DisplayName("진행 상황을 중계하고 끝난 상태를 우리 쪽에도 남긴다")
    void relaysProgressAndKeepsTheFinalStatus() throws Exception {
        String executionId = start();
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
