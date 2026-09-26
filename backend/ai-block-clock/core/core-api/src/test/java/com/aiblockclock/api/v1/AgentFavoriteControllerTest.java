package com.aiblockclock.api.v1;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentFavoriteRepository;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.user.UserEntity;
import com.aiblockclock.storage.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgentFavoriteControllerTest {
    private static final String MY_FAVORITES = "/api/v1/users/me/favorites";

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private AgentFavoriteRepository agentFavoriteRepository;

    private long owner;

    private long me;

    private long other;

    @BeforeEach
    void 판매자와_두_사용자를_준비한다() {
        owner = createUser("학원장");
        me = createUser("김싸피");
        other = createUser("박싸피");
    }

    @Test
    @DisplayName("즐겨찾기를 넣으면 내가 넣었다는 것과 넣은 사람 수를 돌려준다")
    void addsFavoriteAndReturnsState() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);

        add(agent, me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(agent.toString()))
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
    }

    @Test
    @DisplayName("같은 에이전트를 두 번 넣어도 오류 없이 한 번 넣은 것과 같다")
    void addingTwiceIsIdempotent() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);
        add(agent, me);

        add(agent, me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));

        assertThat(agentFavoriteRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("넣은 사람 수는 다른 사용자가 넣은 것까지 센다")
    void countsEveryUsersFavorite() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);
        add(agent, other);

        add(agent, me)
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(2));
    }

    @Test
    @DisplayName("빼면 내 즐겨찾기가 사라지고 남이 넣은 것만 센다")
    void removesFavoriteAndReturnsState() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);
        add(agent, me);
        add(agent, other);

        remove(agent, me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(agent.toString()))
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
    }

    @Test
    @DisplayName("넣지 않은 것을 빼도 오류 없이 지금 상태를 돌려준다")
    void removingAbsentFavoriteIsIdempotent() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);

        remove(agent, me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(0));

        add(agent, me);
        remove(agent, me);

        remove(agent, me)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.favoriteCount").value(0));
    }

    @Test
    @DisplayName("없는 에이전트는 넣을 수도 뺄 수도 없고 MARKET_001 로 거절한다")
    void rejectsUnknownAgent() throws Exception {
        UUID unknown = UUID.randomUUID();

        add(unknown, me)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));

        remove(unknown, me)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));
    }

    @Test
    @DisplayName("공개되지 않은 에이전트는 넣을 수 없고 MARKET_001 로 거절한다")
    void rejectsAgentWithoutPublicVersion() throws Exception {
        UUID draft = draftAgent("만드는 중");

        add(draft, me)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MARKET_001"));

        assertThat(agentFavoriteRepository.count()).isZero();
    }

    @Test
    @DisplayName("에이전트 ID 가 UUID 모양이 아니면 COMMON_001 로 거절한다")
    void rejectsMalformedAgentId() throws Exception {
        mockMvc.perform(post("/api/v1/marketplace/agents/not-a-uuid/favorites").with(as(me)).with(csrf().asHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("로그인하지 않으면 즐겨찾기를 넣을 수 없다")
    void rejectsAnonymousAdd() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);

        mockMvc.perform(post(favorites(agent)).with(csrf().asHeader()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    @Test
    @DisplayName("내 즐겨찾기는 가격과 실행 횟수, 소유자, 넣은 시각을 담는다")
    void listsFavoriteWithMarketFields() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_2, "https://cdn.example/icon.png");
        agentGradeMetricRepository.addRuns(agent.toString(), 120, 5, Instant.now());
        add(agent, me);

        mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].agentId").value(agent.toString()))
                .andExpect(jsonPath("$.data.content[0].name").value("성적 분석"))
                .andExpect(jsonPath("$.data.content[0].summary").value("성적을 분석한다"))
                .andExpect(jsonPath("$.data.content[0].iconUrl").value("https://cdn.example/icon.png"))
                .andExpect(jsonPath("$.data.content[0].price").value("7.800000"))
                .andExpect(jsonPath("$.data.content[0].executionCount").value(125))
                .andExpect(jsonPath("$.data.content[0].owner.nickname").value("학원장"))
                .andExpect(jsonPath("$.data.content[0].favoritedAt").isString())
                .andExpect(jsonPath("$.data.content[0].favorited").doesNotExist())
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("한 번도 세지 않은 에이전트는 실행 횟수가 0 이고 아이콘이 없으면 null 이다")
    void listsFreshAgentWithZeroExecutions() throws Exception {
        UUID agent = publicAgent("성적 분석", Tier.TIER_4, null);
        add(agent, me);

        MvcResult result = mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].executionCount").value(0))
                .andExpect(jsonPath("$.data.content[0].price").value("6.000000"))
                .andReturn();

        JsonNode item = data(result).get("content").get(0);
        assertThat(item.has("iconUrl")).isTrue();
        assertThat(item.get("iconUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("내 즐겨찾기는 나중에 넣은 것부터 나오고 남이 넣은 것은 섞이지 않는다")
    void listsOnlyMyFavoritesNewestFirst() throws Exception {
        UUID first = publicAgent("첫째", Tier.TIER_4, null);
        UUID second = publicAgent("둘째", Tier.TIER_4, null);
        UUID third = publicAgent("셋째", Tier.TIER_4, null);
        UUID othersOnly = publicAgent("남의 것", Tier.TIER_4, null);
        add(first, me);
        add(second, me);
        add(third, me);
        add(othersOnly, other);

        MvcResult result = mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(agentIds(result)).containsExactly(third.toString(), second.toString(), first.toString());
    }

    @Test
    @DisplayName("뺐다가 다시 넣으면 다시 넣은 시각의 자리로 올라온다")
    void readdedFavoriteMovesToTop() throws Exception {
        UUID first = publicAgent("첫째", Tier.TIER_4, null);
        UUID second = publicAgent("둘째", Tier.TIER_4, null);
        add(first, me);
        add(second, me);
        remove(first, me);
        add(first, me);

        MvcResult result = mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andReturn();

        assertThat(agentIds(result)).containsExactly(first.toString(), second.toString());
    }

    @Test
    @DisplayName("개수를 정하면 그만큼씩 끊어 주고 커서로 다음 페이지를 이어 준다")
    void pagesWithCursor() throws Exception {
        UUID first = publicAgent("첫째", Tier.TIER_4, null);
        UUID second = publicAgent("둘째", Tier.TIER_4, null);
        UUID third = publicAgent("셋째", Tier.TIER_4, null);
        add(first, me);
        add(second, me);
        add(third, me);

        MvcResult firstPage = mockMvc.perform(get(MY_FAVORITES).param("size", "2").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andReturn();
        String cursor = data(firstPage).get("nextCursor").asText();

        MvcResult secondPage = mockMvc.perform(get(MY_FAVORITES).param("size", "2").param("cursor", cursor)
                        .with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist())
                .andReturn();

        assertThat(agentIds(firstPage)).containsExactly(third.toString(), second.toString());
        assertThat(agentIds(secondPage)).containsExactly(first.toString());
    }

    @Test
    @DisplayName("개수를 정하지 않으면 9개씩 끊어 준다")
    void pagesByNineByDefault() throws Exception {
        for (int i = 0; i < 10; i++) {
            add(publicAgent("에이전트 " + i, Tier.TIER_4, null), me);
        }

        mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(9))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    @DisplayName("공개를 잃은 에이전트는 즐겨찾기 목록에서 빠지고 페이지 크기를 차지하지 않는다")
    void skipsAgentsWithoutPublicVersion() throws Exception {
        UUID shown = publicAgent("공개된 것", Tier.TIER_4, null);
        UUID hidden = draftAgent("공개를 잃은 것");
        add(shown, me);
        agentFavoriteRepository.insertIfAbsent(me, hidden.toString(), Instant.now());

        MvcResult result = mockMvc.perform(get(MY_FAVORITES).param("size", "1").with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andReturn();

        assertThat(agentIds(result)).containsExactly(shown.toString());
    }

    @Test
    @DisplayName("즐겨찾기한 것이 없으면 빈 목록이다")
    void returnsEmptyWhenNothingFavorited() throws Exception {
        mockMvc.perform(get(MY_FAVORITES).with(as(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("개수가 1 보다 작거나 30 을 넘거나 커서가 올바르지 않으면 COMMON_001 로 거절한다")
    void rejectsOutOfRangeSizeOrBrokenCursor() throws Exception {
        mockMvc.perform(get(MY_FAVORITES).param("size", "0").with(as(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        mockMvc.perform(get(MY_FAVORITES).param("size", "31").with(as(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));

        mockMvc.perform(get(MY_FAVORITES).param("cursor", "!!!").with(as(me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_001"));
    }

    @Test
    @DisplayName("로그인하지 않으면 내 즐겨찾기를 볼 수 없다")
    void rejectsAnonymousList() throws Exception {
        mockMvc.perform(get(MY_FAVORITES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_005"));
    }

    private ResultActions add(UUID agent, long user) throws Exception {
        return mockMvc.perform(post(favorites(agent)).with(as(user)).with(csrf().asHeader()));
    }

    private ResultActions remove(UUID agent, long user) throws Exception {
        return mockMvc.perform(delete(favorites(agent)).with(as(user)).with(csrf().asHeader()));
    }

    private static String favorites(UUID agent) {
        return "/api/v1/marketplace/agents/" + agent + "/favorites";
    }

    private UUID publicAgent(String name, Tier tier, String thumbnailUrl) {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AgentEntity agent = agentRepository.save(new AgentEntity(agentId, owner, name, "성적을 분석한다",
                "학생별 점수 표를 받아 비교한다", null, null, thumbnailUrl, versionId, 1));
        agentVersionRepository.saveAndFlush(new AgentVersionEntity(versionId, agent, 1, UUID.randomUUID(),
                UUID.randomUUID(), 1L, 2, new BigDecimal("6.000000"), tier, PublicationState.PUBLISHED));

        return agentId;
    }

    private UUID draftAgent(String name) {
        UUID agentId = UUID.randomUUID();
        agentRepository.saveAndFlush(new AgentEntity(agentId, owner, name, "성적을 분석한다",
                "학생별 점수 표를 받아 비교한다", null, null, null, null, null));

        return agentId;
    }

    private List<String> agentIds(MvcResult result) throws Exception {
        return data(result).get("content").valueStream()
                .map(item -> item.get("agentId").asText())
                .toList();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long createUser(String nickname) {
        String walletAddress = "0x" + UUID.randomUUID().toString().replace("-", "") + "00000000";
        UserEntity user = userRepository.saveAndFlush(UserEntity.of(walletAddress));
        user.completeOnboarding(nickname, null, Instant.parse("2026-09-23T01:02:03Z"));
        userRepository.flush();

        return user.getId();
    }

    private RequestPostProcessor as(long userId) {
        return authentication(authenticated(UserId.of(userId), null, List.of()));
    }
}
