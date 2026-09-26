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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class MarketplaceAgentControllerTest {
    private static final String MARKETPLACE = "/api/v1/marketplace/agents";
    private static final Instant REGISTERED = Instant.parse("2026-09-01T00:00:00Z");

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

    private long buyer;

    private long seller;

    @BeforeEach
    void 구매자와_판매자를_준비한다() {
        buyer = createUser("구매자");
        seller = createUser("학원장");
        given(recommendClient.upsert(anyList())).willReturn(new IndexOutcome("upsert", 1, 1, "v1"));
    }

    @Test
    @DisplayName("최근에 등록한 에이전트부터 담는다")
    void listsLatestFirst() throws Exception {
        String older = registerAgent("성적 분석", "성적을 분석한다", 1);
        String newer = registerAgent("가정통신문", "안내문을 쓴다", 2);

        mockMvc.perform(get(MARKETPLACE).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].agentId").value(newer))
                .andExpect(jsonPath("$.data.content[1].agentId").value(older))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("검색어가 이름이나 한 줄 소개에 들어 있는 에이전트만 담는다")
    void searchesNameAndSummary() throws Exception {
        String byName = registerAgent("성적 분석", "점수를 정리한다", 1);
        String bySummary = registerAgent("리포트 작성", "성적표로 리포트를 쓴다", 2);
        registerAgent("가정통신문", "안내문을 쓴다", 3);

        mockMvc.perform(get(MARKETPLACE).param("keyword", "성적").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].agentId").value(bySummary))
                .andExpect(jsonPath("$.data.content[1].agentId").value(byName));
    }

    @Test
    @DisplayName("한 쪽을 넘기면 다음 쪽 커서로 나머지를 이어 받는다")
    void pagesThroughTheRest() throws Exception {
        String first = registerAgent("첫째 에이전트", "첫째", 3);
        String second = registerAgent("둘째 에이전트", "둘째", 2);
        String third = registerAgent("셋째 에이전트", "셋째", 1);

        MvcResult page = mockMvc.perform(get(MARKETPLACE).param("size", "2").with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].agentId").value(first))
                .andExpect(jsonPath("$.data.content[1].agentId").value(second))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();

        String cursor = mapper.readTree(page.getResponse().getContentAsString())
                .get("data").get("nextCursor").asText();

        mockMvc.perform(get(MARKETPLACE).param("size", "2").param("cursor", cursor).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].agentId").value(third))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("공개 버전이 없는 에이전트는 목록에 나오지 않는다")
    void leavesUnpublishedOut() throws Exception {
        String published = registerAgent("성적 분석", "성적을 분석한다", 1);
        String hidden = registerAgent("가정통신문", "안내문을 쓴다", 2);
        unpublish(hidden);

        mockMvc.perform(get(MARKETPLACE).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].agentId").value(published));
    }

    @Test
    @DisplayName("목록 한 줄에 판매가와 실행 지표, 소유자 닉네임을 담는다")
    void fillsSummaryFields() throws Exception {
        String agentId = registerAgent("성적 분석", "성적을 분석한다", 1);
        promote(agentId, Tier.TIER_1);
        count(agentId, 9, 1, 2, new BigDecimal("4.50"));

        mockMvc.perform(get(MARKETPLACE).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].name").value("성적 분석"))
                .andExpect(jsonPath("$.data.content[0].summary").value("성적을 분석한다"))
                .andExpect(jsonPath("$.data.content[0].iconUrl").isEmpty())
                .andExpect(jsonPath("$.data.content[0].price").value("9.000000"))
                .andExpect(jsonPath("$.data.content[0].executionCount").value(10))
                .andExpect(jsonPath("$.data.content[0].averageRating").value(4.5))
                .andExpect(jsonPath("$.data.content[0].reviewCount").value(2))
                .andExpect(jsonPath("$.data.content[0].favorited").value(false))
                .andExpect(jsonPath("$.data.content[0].owner.nickname").value("학원장"))
                .andExpect(jsonPath("$.data.content[0].createdAt").value("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("세어 본 적 없는 에이전트는 실행 0 회, 평점 없음으로 내린다")
    void fillsFreshMetric() throws Exception {
        registerAgent("성적 분석", "성적을 분석한다", 1);

        mockMvc.perform(get(MARKETPLACE).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].executionCount").value(0))
                .andExpect(jsonPath("$.data.content[0].averageRating").isEmpty())
                .andExpect(jsonPath("$.data.content[0].reviewCount").value(0));
    }

    @Test
    @DisplayName("개수가 30 을 넘으면 400 이다")
    void rejectsTooLargeSize() throws Exception {
        mockMvc.perform(get(MARKETPLACE).param("size", "31").with(as(buyer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("상세에 소개와 판매가, 실행 지표에서 구한 성공률을 담는다")
    void getsDetail() throws Exception {
        String agentId = registerAgent("성적 분석", "성적을 분석한다", 1);
        count(agentId, 9, 1, 2, new BigDecimal("4.50"));

        mockMvc.perform(get(MARKETPLACE + "/" + agentId).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(agentId))
                .andExpect(jsonPath("$.data.name").value("성적 분석"))
                .andExpect(jsonPath("$.data.summary").value("성적을 분석한다"))
                .andExpect(jsonPath("$.data.description").value("학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다"))
                .andExpect(jsonPath("$.data.usageExample").value("3월 성적표를 올린다"))
                .andExpect(jsonPath("$.data.iconUrl").isEmpty())
                .andExpect(jsonPath("$.data.images").isEmpty())
                .andExpect(jsonPath("$.data.price").value("6.000000"))
                .andExpect(jsonPath("$.data.executionCount").value(10))
                .andExpect(jsonPath("$.data.successRate").value(0.9))
                .andExpect(jsonPath("$.data.averageRating").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(0))
                .andExpect(jsonPath("$.data.owner.userId").value(Long.toString(seller)))
                .andExpect(jsonPath("$.data.owner.nickname").value("학원장"))
                .andExpect(jsonPath("$.data.owner.profileImageUrl").isEmpty())
                .andExpect(jsonPath("$.data.createdAt").value("2026-09-01T00:00:00Z"));
    }

    @Test
    @DisplayName("실행이 한 번도 없으면 성공률을 0 으로 내린다")
    void reportsZeroSuccessRateWithoutRuns() throws Exception {
        String agentId = registerAgent("성적 분석", "성적을 분석한다", 1);

        mockMvc.perform(get(MARKETPLACE + "/" + agentId).with(as(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.executionCount").value(0))
                .andExpect(jsonPath("$.data.successRate").value(0.0));
    }

    @Test
    @DisplayName("없는 에이전트를 열면 MARKET_001 로 404 다")
    void rejectsMissingAgent() throws Exception {
        mockMvc.perform(get(MARKETPLACE + "/" + UUID.randomUUID()).with(as(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));
    }

    @Test
    @DisplayName("공개 버전이 없는 에이전트를 열어도 MARKET_001 로 404 다")
    void rejectsUnpublishedAgent() throws Exception {
        String agentId = registerAgent("성적 분석", "성적을 분석한다", 1);
        unpublish(agentId);

        mockMvc.perform(get(MARKETPLACE + "/" + agentId).with(as(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));
    }

    private String registerAgent(String name, String summary, int daysAfterRegistered) throws Exception {
        UUID nodeId = NodeDefinitionSeeder.seed(nodeRepository, seller);

        MvcResult workflow = mockMvc.perform(post("/api/v1/workflows")
                        .with(as(seller))
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
                        .with(as(seller))
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workflowId": "%s", "revision": 1,
                                  "name": "%s", "summary": "%s",
                                  "description": "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                                  "usageExample": "3월 성적표를 올린다"
                                }
                                """.formatted(workflowId, name, summary)))
                .andExpect(status().isCreated())
                .andReturn();

        String agentId = mapper.readTree(registered.getResponse().getContentAsString())
                .get("data").get("agentId").asText();

        registeredAt(agentId, REGISTERED.plus(daysAfterRegistered - 1L, ChronoUnit.DAYS));

        return agentId;
    }

    private void registeredAt(String agentId, Instant createdAt) {
        entityManager.flush();
        entityManager.createNativeQuery("update agent set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, agentId)
                .executeUpdate();
        entityManager.clear();
    }

    private void unpublish(String agentId) {
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update agent set public_version_id = null, public_version_no = null where id = ?1")
                .setParameter(1, agentId)
                .executeUpdate();
        entityManager.clear();
    }

    private void promote(String agentId, Tier tier) {
        entityManager.flush();
        entityManager.createNativeQuery("update agent_version set tier = ?1 where agent_id = ?2")
                .setParameter(1, tier.name())
                .setParameter(2, agentId)
                .executeUpdate();
        entityManager.clear();
    }

    private void count(String agentId, long successes, long failures, int reviews, BigDecimal rating) {
        entityManager.createNativeQuery("""
                        insert into agent_grade_metric
                            (agent_id, successful_runs, supplier_failures, review_count, average_rating, measured_at)
                        values (?1, ?2, ?3, ?4, ?5, ?6)
                        """)
                .setParameter(1, agentId)
                .setParameter(2, successes)
                .setParameter(3, failures)
                .setParameter(4, reviews)
                .setParameter(5, rating)
                .setParameter(6, Instant.now())
                .executeUpdate();
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
