package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentFavoriteEntity;
import com.aiblockclock.storage.agent.AgentFavoriteRepository;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent_favorite;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AgentFavoriteIntegrationTest {
    private static final UserId OWNER = UserId.of(9801L);
    private static final UserId FAN = UserId.of(9802L);
    private static final String USER_AGENT_UNIQUE = "uq_agent_favorite_user_agent";
    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private AgentFavoriteService agentFavoriteService;

    @Autowired
    private AgentFavoriteRepository agentFavoriteRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Autowired
    private DataSource dataSource;

    private AgentId agent;

    @BeforeEach
    void 공개된_에이전트를_하나_둔다() {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        AgentEntity saved = agentRepository.save(new AgentEntity(agentId, OWNER.value(), "성적 분석",
                "성적을 분석한다", "학생별 점수 표를 받아 비교한다", null, null, null, versionId, 1));
        agentVersionRepository.save(new AgentVersionEntity(versionId, saved, 1, UUID.randomUUID(),
                UUID.randomUUID(), 1L, 2, new BigDecimal("6.000000"), Tier.TIER_4, PublicationState.PUBLISHED));
        agent = AgentId.of(agentId);
    }

    @AfterEach
    void cleanUp() {
        agentFavoriteRepository.deleteAllInBatch();
        agentVersionRepository.deleteAllInBatch();
        agentRepository.deleteAllInBatch();
    }

    @Test
    void 같은_사용자가_같은_에이전트를_동시에_넣어도_모두_성공하고_한_줄만_남는다() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(CONCURRENT_REQUESTS);

        try (var executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)) {
            List<Future<AgentFavorite>> requests = IntStream.range(0, CONCURRENT_REQUESTS)
                    .mapToObj(i -> executor.submit(() -> {
                        barrier.await(10, TimeUnit.SECONDS);

                        return agentFavoriteService.add(FAN, agent);
                    }))
                    .toList();

            for (Future<AgentFavorite> request : requests) {
                AgentFavorite favorite = request.get(20, TimeUnit.SECONDS);

                assertThat(favorite.favorited()).isTrue();
                assertThat(favorite.favoriteCount()).isEqualTo(1);
            }
        }

        assertThat(agentFavoriteRepository.findAll()).singleElement()
                .extracting(AgentFavoriteEntity::getUserId, AgentFavoriteEntity::getAgentId)
                .containsExactly(FAN.value(), agent.value());
    }

    @Test
    void 이미_넣은_것을_다시_넣으면_처음_넣은_시각을_덮지_않는다() {
        agentFavoriteService.add(FAN, agent);
        Instant first = agentFavoriteRepository.findAll().getFirst().getCreatedAt();

        agentFavoriteService.add(FAN, agent);

        assertThat(agentFavoriteRepository.findAll()).singleElement()
                .extracting(AgentFavoriteEntity::getCreatedAt)
                .isEqualTo(first);
    }

    @Test
    void 같은_사용자와_에이전트의_두_번째_줄은_유니크_제약이_막는다() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String sql = "INSERT INTO agent_favorite (user_id, agent_id, created_at) VALUES (?, ?, CURRENT_TIMESTAMP(6))";
        jdbc.update(sql, FAN.value(), agent.value().toString());

        assertThatThrownBy(() -> jdbc.update(sql, FAN.value(), agent.value().toString()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> assertThat(exception.getMessage().toLowerCase(Locale.ROOT))
                        .contains(USER_AGENT_UNIQUE));
    }

    @Test
    void 다른_사용자는_같은_에이전트를_따로_넣을_수_있다() {
        agentFavoriteService.add(FAN, agent);

        AgentFavorite mine = agentFavoriteService.add(OWNER, agent);

        assertThat(mine.favoriteCount()).isEqualTo(2);
        assertThat(agentFavoriteRepository.count()).isEqualTo(2);
    }

    @Test
    void 빼면_그_사용자의_줄만_지운다() {
        agentFavoriteService.add(FAN, agent);
        agentFavoriteService.add(OWNER, agent);

        AgentFavorite removed = agentFavoriteService.remove(FAN, agent);

        assertThat(removed.favorited()).isFalse();
        assertThat(removed.favoriteCount()).isEqualTo(1);
        assertThat(agentFavoriteRepository.findAll()).extracting(AgentFavoriteEntity::getUserId)
                .containsExactly(OWNER.value());
    }
}
