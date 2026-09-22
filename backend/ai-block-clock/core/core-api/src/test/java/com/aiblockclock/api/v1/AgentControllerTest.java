package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.graph.GraphNodeEntity;
import com.aiblockclock.storage.graph.GraphNodeRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentControllerTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Autowired
    private GraphNodeRepository graphNodeRepository;

    private long owner;

    private long other;

    private UUID nodeId;

    @BeforeEach
    void 두_사용자와_노드_정의를_준비한다() {
        owner = createUser();
        other = createUser();
        nodeId = NodeDefinitionSeeder.seed(nodeRepository, owner);
    }

    @Test
    @DisplayName("내 워크플로우를 굳히면 201 과 에이전트 ID · 단가가 나온다")
    void registersAgentFromOwnWorkflow() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(owner, registerBody(workflowId, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.agentId").isNotEmpty())
                .andExpect(jsonPath("$.data.name").value("성적 분석 에이전트"))
                .andExpect(jsonPath("$.data.price").value("6.000000"));
    }

    @Test
    @DisplayName("노드를 만들어 캔버스에 놓고 저장한 뒤 에이전트로 굳히기까지 HTTP 로만 간다")
    void registersAgentThroughTheWholeHttpChain() throws Exception {
        String first = createNode("성적 분석", "당신은 학원 성적 데이터 분석가입니다.");
        String second = createNode("가정통신문 작성", "분석 결과로 가정통신문을 씁니다.");

        String workflowId = createWorkflow(UUID.fromString(first), UUID.fromString(second));

        MvcResult registered = mockMvc.perform(register(owner, registerBody(workflowId, 1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price").value("6.000000"))
                .andReturn();

        String agentId = mapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("agentId").asText();

        AgentEntity agent = agentRepository.findById(UUID.fromString(agentId)).orElseThrow();
        AgentVersionEntity version =
                agentVersionRepository.findById(agent.getPublicVersionId()).orElseThrow();

        assertThat(agent.getOwnerId()).isEqualTo(owner);
        assertThat(version.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(graphNodeRepository.findByGraphIdOrderByIdAsc(version.getGraphId()))
                .extracting(GraphNodeEntity::getReferenceId)
                .containsExactlyInAnyOrder(UUID.fromString(first), UUID.fromString(second));
    }

    @Test
    @DisplayName("분기가 있는 그래프는 아직 저장 단계에서 막힌다")
    void doesNotAcceptBranchingYet() throws Exception {
        String node = createNode("성적 분석", "당신은 학원 성적 데이터 분석가입니다.");

        mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "분기",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n3", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" },
                                    { "edgeKey": "e2", "sourceNodeKey": "n1", "targetNodeKey": "n3" }
                                  ]
                                }
                                """.formatted(node, node, node)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_013"));
    }

    @Test
    @DisplayName("보던 회차가 아니면 409 다")
    void rejectsStaleRevision() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(owner, registerBody(workflowId, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_016"))
                .andExpect(jsonPath("$.error.data.currentRevision").value(1));
    }

    @Test
    @DisplayName("남의 워크플로우는 굳힐 수 없다")
    void rejectsOtherOwnersWorkflow() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(other, registerBody(workflowId, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));
    }

    @Test
    @DisplayName("남의 노드가 섞여 있으면 굳힐 수 없다")
    void rejectsForeignNode() throws Exception {
        String workflowId = createWorkflow(nodeId, NodeDefinitionSeeder.seed(nodeRepository, other));

        mockMvc.perform(register(owner, registerBody(workflowId, 1)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AGENT_002"));
    }

    @Test
    @DisplayName("이름이 한 글자이거나 설명이 비면 400 이다")
    void rejectsInvalidProfile() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(owner, """
                {
                  "workflowId": "%s", "revision": 1,
                  "name": "짧", "summary": "성적을 분석한다", "description": ""
                }
                """.formatted(workflowId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("주체를 밝히지 않으면 401 이다")
    void rejectsMissingOwner() throws Exception {
        mockMvc.perform(post("/api/v1/agents")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(UUID.randomUUID().toString(), 1)))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.RequestBuilder register(long userId, String body) {
        return post("/api/v1/agents")
                .with(as(userId))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String registerBody(String workflowId, long revision) {
        return """
                {
                  "workflowId": "%s",
                  "revision": %d,
                  "name": "성적 분석 에이전트",
                  "summary": "성적을 분석한다",
                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                  "usageExample": "성적표 엑셀을 올리면 분석 리포트가 나옵니다",
                  "onboardingMessage": "무엇을 분석할까요?"
                }
                """.formatted(workflowId, revision);
    }

    private String createNode(String name, String prompt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/nodes")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "%s", "prompt": "%s" }
                                """.formatted(name, prompt)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("nodeId").asText();
    }

    private String createWorkflow(UUID firstNode, UUID secondNode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "성적 분석",
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                                  ]
                                }
                                """.formatted(firstNode, secondNode)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
