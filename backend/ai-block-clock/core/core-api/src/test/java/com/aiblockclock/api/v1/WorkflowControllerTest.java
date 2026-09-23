package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkflowControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    private long owner;

    private long other;

    private UUID nodeId;

    @BeforeEach
    void 두_사용자와_노드_정의를_준비한다() {
        owner = createUser();
        other = createUser();
        nodeId = NodeDefinitionSeeder.seed(nodeRepository, owner);
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

    private String chainBody(String name) {
        return """
                {
                  "name": "%s",
                  "nodes": [
                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                  ],
                  "edges": [
                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                  ]
                }
                """.formatted(name, nodeId, nodeId);
    }

    private String create(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainBody(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.revision").value(1))
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
    }

    @Test
    @DisplayName("저장하고, 조회하고, 수정하고, 목록에서 보고, 삭제한다")
    void lifecycle() throws Exception {
        String workflowId = create("성적 분석 리포트");

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("성적 분석 리포트"))
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.nodes[0].nodeKey").value("n1"))
                .andExpect(jsonPath("$.data.nodes[0].versionId").doesNotExist())
                .andExpect(jsonPath("$.data.edges[0].edgeKey").value("e1"))
                .andExpect(jsonPath("$.data.lastExecutedAt").doesNotExist());

        mockMvc.perform(put("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "이름을 고쳤다",
                                  "revision": 1,
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("이름을 고쳤다"))
                .andExpect(jsonPath("$.data.revision").value(2));

        mockMvc.perform(get("/api/v1/workflows")
                        .with(as(owner))
                        .param("keyword", "고쳤다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].workflowId").value(workflowId))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workflowId").value(workflowId));

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_002"));
    }

    // 예상 비용은 견적 API(POST /api/v1/executions/cost-estimates)가 알려 준다. 늘 0 이던 칸을 되살리지 않는다.
    @Test
    void 저장과_조회_응답에는_예상_비용과_통화_칸이_없다() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chainBody("성적 분석 리포트")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.estimatedCost").doesNotExist())
                .andExpect(jsonPath("$.data.currency").doesNotExist())
                .andReturn();
        String workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedCost").doesNotExist())
                .andExpect(jsonPath("$.data.currency").doesNotExist());
    }

    @Test
    @DisplayName("뒤섞어 보낸 노드도 다시 읽으면 연결 순서대로 나온다")
    void returnsNodesInConnectedOrder() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "뒤섞어 보낸다",
                                  "nodes": [
                                    { "nodeKey": "n3", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e2", "sourceNodeKey": "n2", "targetNodeKey": "n3" },
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                                  ]
                                }
                                """.formatted(nodeId, nodeId, nodeId)))
                .andExpect(status().isCreated())
                .andReturn();

        String workflowId = mapper.readTree(created.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].nodeKey").value("n1"))
                .andExpect(jsonPath("$.data.nodes[1].nodeKey").value("n2"))
                .andExpect(jsonPath("$.data.nodes[2].nodeKey").value("n3"))
                .andExpect(jsonPath("$.data.edges[0].edgeKey").value("e1"))
                .andExpect(jsonPath("$.data.edges[1].edgeKey").value("e2"));
    }

    @Test
    @DisplayName("본문으로 실어 보낸 단계는 노드로 만들어져 배치되고, 응답이 그 ID 를 알려준다")
    void createsNodeCarriedByPlacement() throws Exception {
        long before = nodeRepository.count();

        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "새 단계로 만든다",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "nodeType": "LLM",
                                      "name": "가정통신문 작성", "prompt": "분석 결과로 가정통신문을 쓴다" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                                  ]
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nodes[0].sourceId").value(nodeId.toString()))
                .andExpect(jsonPath("$.data.nodes[1].sourceId").isNotEmpty())
                .andReturn();

        String createdId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("nodes").get(1).get("sourceId").asText();

        assertThat(nodeRepository.count()).isEqualTo(before + 1);
        assertThat(nodeRepository.findById(UUID.fromString(createdId)).orElseThrow())
                .satisfies(node -> {
                    assertThat(node.getName()).isEqualTo("가정통신문 작성");
                    assertThat(node.getOwnerId()).isEqualTo(owner);
                });
    }

    @Test
    @DisplayName("sourceId 와 새 노드 본문을 함께 보내면 400 이다")
    void rejectsBothSourceAndDraft() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "둘 다 보낸다",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM", "sourceId": "%s",
                                      "name": "가정통신문 작성", "prompt": "분석 결과로 가정통신문을 쓴다" }
                                  ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("sourceId 도 본문도 없는 배치는 400 이다")
    void rejectsPlacementWithoutSourceOrDraft() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "빈 배치",
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE" } ],
                                  "edges": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("에이전트 자리에는 새 노드 본문을 실을 수 없다")
    void rejectsDraftOnAgentPlacement() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "에이전트 자리에 본문",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "AGENT",
                                      "name": "가정통신문 작성", "prompt": "분석 결과로 가정통신문을 쓴다" }
                                  ],
                                  "edges": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("본문을 실으면서 종류를 LLM 으로 밝히지 않으면 400 이다")
    void rejectsBodyWithoutLlmType() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "종류 없는 본문",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE",
                                      "name": "가정통신문 작성", "prompt": "분석 결과로 가정통신문을 쓴다" }
                                  ],
                                  "edges": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("가리킨 노드가 밝힌 종류가 아니면 종류가 다르다고 거절한다")
    void rejectsPlacementOfAnotherKind() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "도구로 놓는다",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "TOOL", "sourceId": "%s" }
                                  ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_017"))
                .andExpect(jsonPath("$.error.data.nodeType").value("TOOL"));
    }

    @Test
    @DisplayName("팔레트에서 끌어다 놓은 내 LLM 노드는 종류를 밝혀도 그대로 놓인다")
    void placesExistingLlmNodeByReference() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "팔레트에서 끌어 놓는다",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM", "sourceId": "%s" }
                                  ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.nodes[0].sourceId").value(nodeId.toString()));
    }

    @Test
    @DisplayName("주체를 밝히지 않으면 401 이다")
    void rejectsMissingOwner() throws Exception {
        mockMvc.perform(get("/api/v1/workflows"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("남의 워크플로우는 조회도 삭제도 못 한다")
    void rejectsOtherOwner() throws Exception {
        String workflowId = create("남의 것");

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));

        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId)
                        .with(as(other))
                        .with(csrf().asHeader()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));
    }

    @Test
    @DisplayName("입력 출처는 배치마다 정하고, 비우면 앞 노드 결과를 받는다")
    void inputSourceIsPerPlacement() throws Exception {
        MvcResult saved = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "입력 출처",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s",
                                      "inputSource": "USER" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [ { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" } ]
                                }
                                """.formatted(nodeId, nodeId)))
                .andExpect(status().isCreated())
                .andReturn();

        String workflowId = mapper.readTree(saved.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();

        mockMvc.perform(get("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].inputSource").value("USER"))
                .andExpect(jsonPath("$.data.nodes[1].inputSource").value("PREV"));

        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("낡은 회차로 수정하면 충돌로 막는다")
    void rejectsStaleRevision() throws Exception {
        String workflowId = create("회차 충돌");

        mockMvc.perform(put("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "회차 충돌",
                                  "revision": 99,
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(nodeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_016"));
    }

    @Test
    @DisplayName("순환이 있으면 저장하지 않는다")
    void rejectsCycle() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "순환",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" },
                                    { "edgeKey": "e2", "sourceNodeKey": "n2", "targetNodeKey": "n1" }
                                  ]
                                }
                                """.formatted(nodeId, nodeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_005"));
    }

    @Test
    @DisplayName("노드가 없으면 저장하지 않는다")
    void rejectsEmptyNodes() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "빈 캔버스", "nodes": [], "edges": [] }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("공개되지 않은 에이전트는 놓을 수 없다")
    void rejectsAgentPlacement() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "에이전트 배치",
                                  "nodes": [ { "nodeKey": "n1", "sourceType": "AGENT", "sourceId": "%s" } ],
                                  "edges": []
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_015"));
    }

    @Test
    @DisplayName("워크플로우 ID 자리에 아무 문자열이나 오면 400 이다")
    void rejectsMalformedWorkflowId() throws Exception {
        mockMvc.perform(get("/api/v1/workflows/{id}", "워크플로우아님")
                        .with(as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"))
                .andExpect(jsonPath("$.error.data.field").value("workflowId"));
    }

    @Test
    @DisplayName("목록 조회 크기가 범위를 벗어나면 400 이다")
    void rejectsSizeOutOfRange() throws Exception {
        mockMvc.perform(get("/api/v1/workflows")
                        .with(as(owner))
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("읽을 수 없는 본문은 400 이다")
    void rejectsUnreadableBody() throws Exception {
        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }
}
