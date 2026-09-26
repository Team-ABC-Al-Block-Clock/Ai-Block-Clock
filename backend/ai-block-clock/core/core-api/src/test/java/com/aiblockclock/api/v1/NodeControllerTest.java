package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeRefFileRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NodeControllerTest {
    @org.junit.jupiter.api.io.TempDir
    static java.nio.file.Path fileRoot;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @DynamicPropertySource
    static void 파일은_임시_폴더에_쌓는다(DynamicPropertyRegistry registry) {
        registry.add("file-store.root", () -> fileRoot);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeRefFileRepository refFileRepository;

    private long owner;

    private long other;

    @BeforeEach
    void 두_사용자를_준비한다() {
        owner = createUser();
        other = createUser();
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        return user.getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken.authenticated(
                        UserId.of(userId), null, java.util.List.of()
                ));
    }

    private static String body(String name, String prompt) {
        return """
                { "name": "%s", "prompt": "%s" }
                """.formatted(name, prompt);
    }

    private String create(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(name, "당신은 학원 성적 데이터 분석가입니다.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.baseCost").value("0.013500"))
                .andExpect(jsonPath("$.data.currency").value("ABC"))
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("nodeId").asText();
    }

    @Test
    @DisplayName("출력 형식으로 JSON 예시를 적으면 저장하고 돌려준다. 비우면 글이다")
    void savesOutputKind() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "시장 조사", "prompt": "시장을 조사한다",
                                  "output": { "kind": "JSON", "example": { "competition_level": "high", "note": null } } }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.output.kind").value("JSON"))
                .andExpect(jsonPath("$.data.output.example.competition_level").value("high"))
                .andReturn();

        String nodeId = mapper.readTree(created.getResponse().getContentAsString()).get("data").get("nodeId").asText();

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.output.kind").value("JSON"))
                .andExpect(jsonPath("$.data.output.example.competition_level").value("high"))
                .andExpect(jsonPath("$.data.output.example.note").isEmpty());

        String textId = create("성적 분석");

        mockMvc.perform(get("/api/v1/nodes/{id}", textId).with(as(owner)))
                .andExpect(jsonPath("$.data.output.kind").value("TEXT"))
                .andExpect(jsonPath("$.data.output.example").doesNotExist());
    }

    @Test
    @DisplayName("JSON 인데 예시가 없거나 키 있는 객체가 아니면, 글인데 예시가 있으면 400 이다")
    void rejectsMismatchedOutput() throws Exception {
        for (String output : List.of(
                "{ \"kind\": \"JSON\" }",
                "{ \"kind\": \"JSON\", \"example\": {} }",
                "{ \"kind\": \"JSON\", \"example\": [ \"a\" ] }",
                "{ \"kind\": \"JSON\", \"example\": \"biz.market_research@1\" }",
                "{ \"kind\": \"TEXT\", \"example\": { \"a\": 1 } }")) {
            mockMvc.perform(post("/api/v1/nodes")
                            .with(as(owner))
                            .with(csrf().asHeader())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"name\": \"시장 조사\", \"prompt\": \"시장을 조사한다\", \"output\": " + output + " }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("COMMON_001"));
        }
    }

    @Test
    @DisplayName("모델로 Gemini 2.5 Pro 를 고르면 그대로 저장하고 돌려준다")
    void savesProModel() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "성적 분석", "prompt": "점수를 비교한다", "model": "GEMINI_2_5_PRO" }
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        String nodeId = mapper.readTree(created.getResponse().getContentAsString()).get("data").get("nodeId").asText();

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.model").value("GEMINI_2_5_PRO"));
    }

    @Test
    @DisplayName("자료를 붙이면 노드 상세에 딸려 나오고, 떼면 사라진다")
    void attachesAndDetachesRefFiles() throws Exception {
        String nodeId = create("채점");

        MvcResult attached = mockMvc.perform(multipart("/api/v1/nodes/{id}/ref-files", nodeId)
                        .file(refFile("채점 기준표.pdf", "1등급은 90점"))
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.filename").value("채점 기준표.pdf"))
                .andReturn();

        String refFileId = mapper.readTree(attached.getResponse().getContentAsString())
                .get("data").get("refFileId").asText();

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refFiles[0].filename").value("채점 기준표.pdf"));

        mockMvc.perform(delete("/api/v1/nodes/{id}/ref-files/{refFileId}", nodeId, refFileId)
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId).with(as(owner)))
                .andExpect(jsonPath("$.data.refFiles").isEmpty());
    }

    @Test
    @DisplayName("계약이 받지 않는 형식은 붙일 때 막는다")
    void refusesARefFileTheEngineWouldReject() throws Exception {
        String nodeId = create("채점");

        mockMvc.perform(multipart("/api/v1/nodes/{id}/ref-files", nodeId)
                        .file(refFile("기준표.xlsx", "점수"))
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("NODE_006"));
    }

    @Test
    @DisplayName("남의 노드에는 자료를 붙일 수 없다")
    void refusesToAttachToSomeoneElsesNode() throws Exception {
        String nodeId = create("채점");

        mockMvc.perform(multipart("/api/v1/nodes/{id}/ref-files", nodeId)
                        .file(refFile("기준표.pdf", "점수"))
                        .with(as(other)).with(csrf().asHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NODE_002"));
    }

    @Test
    @DisplayName("노드를 지우면 붙어 있던 자료 행도 지우고, 파일은 남긴다")
    void removesRefFileRowsWithNode() throws Exception {
        String nodeId = create("채점");

        mockMvc.perform(multipart("/api/v1/nodes/{id}/ref-files", nodeId)
                        .file(refFile("채점 기준표.pdf", "1등급은 90점"))
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isOk());

        assertThat(refFileRepository.findByNodeDefinitionIdOrderByOrdinal(UUID.fromString(nodeId))).isEmpty();
        try (var stored = Files.list(fileRoot.resolve("nodes").resolve(nodeId))) {
            assertThat(stored).hasSize(1);
        }
    }

    private static MockMultipartFile refFile(String filename, String content) {
        return new MockMultipartFile("file", filename,
                MediaType.APPLICATION_OCTET_STREAM_VALUE, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("만들고, 조회하고, 수정하고, 목록에서 보고, 삭제한다")
    void lifecycle() throws Exception {
        String nodeId = create("성적 분석");

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("성적 분석"))
                .andExpect(jsonPath("$.data.prompt").value("당신은 학원 성적 데이터 분석가입니다."))
                .andExpect(jsonPath("$.data.model").value("GEMINI_2_5_FLASH"));

        mockMvc.perform(put("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("가정통신문 작성", "당신은 교육 심리 전문가입니다.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("가정통신문 작성"));

        mockMvc.perform(get("/api/v1/nodes")
                        .with(as(owner))
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].nodeId").value(nodeId))
                .andExpect(jsonPath("$.data.content[0].prompt").doesNotExist());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NODE_001"));
    }

    @Test
    @DisplayName("프롬프트가 비어 있으면 만들 수 없다")
    void rejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("빈 프롬프트", "   ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("남의 노드는 조회도 수정도 못 한다")
    void rejectsOtherOwner() throws Exception {
        String nodeId = create("남의 노드");

        mockMvc.perform(get("/api/v1/nodes/{id}", nodeId)
                        .with(as(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NODE_002"));

        mockMvc.perform(put("/api/v1/nodes/{id}", nodeId)
                        .with(as(other))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("탈취", "남의 것을 고친다.")))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("워크플로우에 놓인 노드는 삭제할 수 없다")
    void rejectsDeleteWhilePlaced() throws Exception {
        String nodeId = create("배치된 노드");

        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "노드 하나짜리",
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isCreated())
                .andReturn();

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NODE_004"));

        String workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
    }
}
