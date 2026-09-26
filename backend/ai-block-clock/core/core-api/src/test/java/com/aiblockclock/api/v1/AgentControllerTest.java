package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.execution.ExecutionStatus;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.execution.ExecutionEntity;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@Tag("integration")
// 금액을 셈하기 쉽게 새로 만든 노드의 원가를 3 ABC 로 둔다. 기본 원가 자체는 NodeControllerTest 가 본다.
@SpringBootTest(properties = "node.base-cost.default-cost=3")
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

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionSettlementRepository executionSettlementRepository;

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
    @DisplayName("분기 노드 없이 갈라지는 그래프는 저장 단계에서 막힌다")
    void rejectsForkWithoutBranchNode() throws Exception {
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
    @DisplayName("원본 워크플로우를 지워도 굳힌 에이전트가 쓰는 노드는 지울 수 없다")
    void keepsNodesOfRegisteredAgentUndeletable() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(owner, registerBody(workflowId, 1)))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/nodes/{id}", nodeId)
                        .with(as(owner))
                        .with(csrf().asHeader()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NODE_004"));
    }

    @Test
    @DisplayName("회차를 빼먹으면 409 가 아니라 400 이다")
    void rejectsMissingRevision() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(register(owner, """
                {
                  "workflowId": "%s",
                  "name": "성적 분석 에이전트", "summary": "성적을 분석한다",
                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다"
                }
                """.formatted(workflowId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
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

    @Test
    @DisplayName("굳힌 원본을 고친 뒤 PUT 하면 200 과 오른 버전 번호가 나온다")
    void raisesVersionOnUpdate() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);
        String agentId = registerAgent(workflowId);
        replaceWorkflow(workflowId, createNode("요약", "결과를 한 문단으로 줄입니다."));

        mockMvc.perform(update(owner, agentId, updateBody(workflowId, 2, "성적 분석 에이전트")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2))
                .andExpect(jsonPath("$.data.agentId").value(agentId));
    }

    @Test
    @DisplayName("굳힌 그대로 이름만 바꿔 PUT 하면 버전은 1 그대로다")
    void keepsVersionWhenOnlyNameChanges() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);
        String agentId = registerAgent(workflowId);

        mockMvc.perform(update(owner, agentId, updateBody(workflowId, 1, "이름만 바꾼 에이전트")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.name").value("이름만 바꾼 에이전트"));
    }

    @Test
    @DisplayName("남의 에이전트를 PUT 하면 막힌다")
    void rejectsUpdateByOtherOwner() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);
        String agentId = registerAgent(workflowId);

        mockMvc.perform(update(other, agentId, updateBody(workflowId, 1, "남이 고친다")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("WORKFLOW_003"));
    }

    @Test
    @DisplayName("없는 에이전트를 PUT 하면 404 다")
    void rejectsUpdateOfUnknownAgent() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);

        mockMvc.perform(update(owner, UUID.randomUUID().toString(), updateBody(workflowId, 1, "없는 것을 고친다")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("AGENT_003"));
    }

    @Test
    @DisplayName("GET 하면 판매 중인 내 에이전트만 받은 정산금 · 실행 수 · 즐겨찾기 · 7일 추이와 함께 나온다")
    void listsMyAgentsWithSales() throws Exception {
        String agentId = registerAgent(createWorkflow(nodeId, nodeId));
        registerAgentOf(other);
        UUID sold = executionBy(other);
        settle(sold, "n1", agentId, "1.5");
        settle(sold, "n2", agentId, "0.25");
        agentGradeMetricRepository.addRuns(agentId, 2, 1, Instant.now());
        mockMvc.perform(post("/api/v1/marketplace/agents/" + agentId + "/favorites")
                        .with(as(owner)).with(csrf().asHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/agents").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].agentId").value(agentId))
                .andExpect(jsonPath("$.data.content[0].name").value("성적 분석 에이전트"))
                .andExpect(jsonPath("$.data.content[0].summary").value("성적을 분석한다"))
                .andExpect(jsonPath("$.data.content[0].iconUrl").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].price").value("6.000000"))
                .andExpect(jsonPath("$.data.content[0].revenue").value("1.750000"))
                .andExpect(jsonPath("$.data.content[0].executionCount").value(3))
                .andExpect(jsonPath("$.data.content[0].favorited").value(true))
                .andExpect(jsonPath("$.data.content[0].trend.length()").value(7))
                .andExpect(jsonPath("$.data.content[0].trend[6]").value(1))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }

    @Test
    @DisplayName("REVENUE 로 한 개씩 넘기면 받은 정산금이 많은 것부터 커서로 이어 받는다")
    void pagesMyAgentsByRevenue() throws Exception {
        String workflowId = createWorkflow(nodeId, nodeId);
        String poor = registerAgent(workflowId);
        String rich = registerAgent(workflowId);
        settle(executionBy(other), "n1", rich, "3");
        settle(executionBy(other), "n1", poor, "1");

        MvcResult first = mockMvc.perform(get("/api/v1/agents").with(as(owner))
                        .param("sort", "REVENUE").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].agentId").value(rich))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        String nextCursor = mapper.readTree(first.getResponse().getContentAsString())
                .get("data").get("nextCursor").asText();

        mockMvc.perform(get("/api/v1/agents").with(as(owner))
                        .param("sort", "REVENUE").param("size", "1").param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].agentId").value(poor))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("검색어는 한 줄 소개에서도 찾고, 어디에도 없으면 빈 목록이다")
    void findsNothingForUnmatchedKeyword() throws Exception {
        registerAgent(createWorkflow(nodeId, nodeId));

        mockMvc.perform(get("/api/v1/agents").with(as(owner)).param("keyword", "분석한다"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        mockMvc.perform(get("/api/v1/agents").with(as(owner)).param("keyword", "가정통신문"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("내 에이전트 목록은 로그인해야 보고, 정렬 · 개수가 틀리면 400 이다")
    void rejectsInvalidMyAgentsQuery() throws Exception {
        mockMvc.perform(get("/api/v1/agents"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/agents").with(as(owner)).param("sort", "RATING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        mockMvc.perform(get("/api/v1/agents").with(as(owner)).param("size", "31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    private void registerAgentOf(long userId) throws Exception {
        UUID othersNode = NodeDefinitionSeeder.seed(nodeRepository, userId);

        mockMvc.perform(register(userId, registerBody(createWorkflow(userId, othersNode, othersNode), 1)))
                .andExpect(status().isCreated());
    }

    private UUID executionBy(long requester) {
        UUID id = UUID.randomUUID();
        executionRepository.save(new ExecutionEntity(id, UUID.randomUUID(), UUID.randomUUID(), requester, "run-" + id,
                ExecutionStatus.SUCCEEDED, 1, null, null, null, false, new BigDecimal("10")));

        return id;
    }

    private void settle(UUID execution, String stepId, String agentId, String amount) {
        executionSettlementRepository.saveAndFlush(new ExecutionSettlementEntity(
                execution, stepId, "노드", owner, UUID.fromString(agentId), new BigDecimal(amount)));
    }

    private String registerAgent(String workflowId) throws Exception {
        MvcResult result = mockMvc.perform(register(owner, registerBody(workflowId, 1)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("agentId").asText();
    }

    private void replaceWorkflow(String workflowId, String replacingSecondNode) throws Exception {
        mockMvc.perform(put("/api/v1/workflows/{id}", workflowId)
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "고친 원본",
                                  "revision": 1,
                                  "nodes": [
                                    { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "%s" },
                                    { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "%s" }
                                  ],
                                  "edges": [
                                    { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                                  ]
                                }
                                """.formatted(nodeId, replacingSecondNode)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.RequestBuilder update(
            long userId, String agentId, String body) {
        return put("/api/v1/agents/{agentId}", agentId)
                .with(as(userId))
                .with(csrf().asHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String updateBody(String workflowId, long revision, String name) {
        return """
                {
                  "workflowId": "%s",
                  "revision": %d,
                  "name": "%s",
                  "summary": "성적을 분석한다",
                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다"
                }
                """.formatted(workflowId, revision, name);
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
        return createWorkflow(owner, firstNode, secondNode);
    }

    private String createWorkflow(long userId, UUID firstNode, UUID secondNode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(userId))
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
