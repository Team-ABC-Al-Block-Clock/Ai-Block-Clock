package com.aiblockclock.api.v1;

import com.aiblockclock.client.ai.IndexOutcome;
import com.aiblockclock.client.ai.RecommendClient;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.Tier;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
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
class PlaceableItemControllerTest {
    private static final String PLACEABLE = "/api/v1/workflows/node-palette";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private RecommendClient recommendClient;

    private long me;

    private long other;

    @BeforeEach
    void 두_사용자를_준비한다() {
        me = createUser("나");
        other = createUser("남");
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));
    }

    @Test
    @DisplayName("ALL 이면 내 노드는 빼고 공개 에이전트만 담는다")
    void listsAgentsOnly() throws Exception {
        NodeDefinitionSeeder.seed(nodeRepository, me);
        String othersAgent = registerAgent(other);

        mockMvc.perform(get(PLACEABLE).param("scope", "ALL").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].itemId").value(othersAgent))
                .andExpect(jsonPath("$.data.content[0].itemType").value("AGENT"))
                .andExpect(jsonPath("$.data.content[0].nodeType").value("AGENT"))
                .andExpect(jsonPath("$.data.content[0].mine").value(false))
                .andExpect(jsonPath("$.data.content[0].owner.nickname").value("남"))
                .andExpect(jsonPath("$.data.content[0].price").value("6.000000"))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("내 에이전트는 등급이 높아도 원가로, 남의 에이전트는 할증을 붙인 판매가로 내린다")
    void pricesMyAgentAtBaseCost() throws Exception {
        String myAgent = registerAgent(me);
        String othersAgent = registerAgent(other);
        promote(myAgent, Tier.TIER_1);
        promote(othersAgent, Tier.TIER_1);

        mockMvc.perform(get(PLACEABLE).param("scope", "ALL").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].itemId").value(myAgent))
                .andExpect(jsonPath("$.data.content[0].price").value("6.000000"))
                .andExpect(jsonPath("$.data.content[1].itemId").value(othersAgent))
                .andExpect(jsonPath("$.data.content[1].price").value("9.000000"));
    }

    @Test
    @DisplayName("MINE 이면 남의 에이전트는 나오지 않는다")
    void leavesOthersOutForMine() throws Exception {
        registerAgent(other);

        mockMvc.perform(get(PLACEABLE).param("scope", "MINE").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty());
    }

    @Test
    @DisplayName("개수가 30 을 넘으면 400 이다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get(PLACEABLE).param("scope", "ALL").param("size", "31").with(as(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    private String registerAgent(long owner) throws Exception {
        UUID nodeId = NodeDefinitionSeeder.seed(nodeRepository, owner);

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

    private void promote(String agentId, Tier tier) {
        entityManager.flush();
        entityManager.createNativeQuery("update agent_version set tier = ?1 where agent_id = ?2")
                .setParameter(1, tier.name())
                .setParameter(2, agentId)
                .executeUpdate();
        entityManager.clear();
    }

    private long createUser(String nickname) {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = UserEntity.of(walletAddress);
        user.completeOnboarding(nickname, null, Instant.now());

        return userRepository.saveAndFlush(user).getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
