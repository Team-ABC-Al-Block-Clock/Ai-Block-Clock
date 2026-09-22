package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.IndexOutcome;
import com.aiblockclock.client.ai.IndexedAgent;
import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.client.ai.RecommendClientError;
import com.aiblockclock.client.ai.RecommendClientException;
import com.aiblockclock.client.ai.RecommendedAgent;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentRecommendationControllerTest {
    private static final String RECOMMENDATIONS = "/api/v1/marketplace/agents/recommendations";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @MockitoBean
    private RecommendClient recommendClient;

    private long owner;

    private UUID nodeId;

    @BeforeEach
    void 사용자와_노드를_준비한다() {
        owner = createUser();
        nodeId = NodeDefinitionSeeder.seed(nodeRepository, owner);
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));
    }

    @Test
    @DisplayName("굳히면 색인에 공개 메타데이터가 올라간다")
    void pushesRegisteredAgentToTheIndex() throws Exception {
        String agentId = registerAgent();

        ArgumentCaptor<List<IndexedAgent>> sent = ArgumentCaptor.captor();
        then(recommendClient).should().upsert(sent.capture());

        assertThat(sent.getValue()).singleElement()
                .extracting(IndexedAgent::agentId, IndexedAgent::name, IndexedAgent::isPublic)
                .containsExactly(agentId, "성적 분석 에이전트", true);
    }

    @Test
    @DisplayName("자연어로 찾으면 추천 순서대로 공개된 에이전트가 나온다")
    void returnsAgentsFoundByPlainWords() throws Exception {
        String agentId = registerAgent();
        given(recommendClient.recommend(anyString(), anyInt()))
                .willReturn(List.of(new RecommendedAgent(agentId, "성적 분석 에이전트", 0.81, "성적을 분석한다")));

        mockMvc.perform(get(RECOMMENDATIONS).param("query", "성적표로 리포트를 쓰고 싶어").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].agentId").value(agentId))
                .andExpect(jsonPath("$.data.items[0].name").value("성적 분석 에이전트"))
                .andExpect(jsonPath("$.data.items[0].price").value("6.000000"));
    }

    @Test
    @DisplayName("추천 엔진에 닿지 못하면 502 다")
    void failsWhenTheEngineIsUnreachable() throws Exception {
        willThrow(new RecommendClientException(RecommendClientError.REQUEST_FAILED, "engine is down"))
                .given(recommendClient).recommend(anyString(), anyInt());

        mockMvc.perform(get(RECOMMENDATIONS).param("query", "성적표").with(as(owner)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("RECOMMEND_001"));
    }

    @Test
    @DisplayName("아직 색인된 것이 없으면 빈 목록이다")
    void returnsEmptyWhenNothingIsIndexed() throws Exception {
        given(recommendClient.recommend(anyString(), anyInt())).willReturn(List.of());

        mockMvc.perform(get(RECOMMENDATIONS).param("query", "성적표").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private String registerAgent() throws Exception {
        MvcResult workflow = mockMvc.perform(post("/api/v1/workflows")
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
                                  "edges": [ { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" } ]
                                }
                                """.formatted(nodeId, nodeId)))
                .andExpect(status().isCreated())
                .andReturn();

        String workflowId = mapper.readTree(workflow.getResponse().getContentAsString())
                .get("data").get("workflowId").asText();

        MvcResult registered = mockMvc.perform(post("/api/v1/agents")
                        .with(as(owner))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowId": "%s", "revision": 1,
                                  "name": "성적 분석 에이전트", "summary": "성적을 분석한다",
                                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다"
                                }
                                """.formatted(workflowId)))
                .andExpect(status().isCreated())
                .andReturn();

        return mapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("agentId").asText();
    }

    private long createUser() {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";

        return userRepository.saveAndFlush(UserEntity.of(walletAddress)).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
