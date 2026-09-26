package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.storage.agent.AgentGradeHistoryEntity;
import com.aiblockclock.storage.agent.AgentGradeHistoryRepository;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

// 이력은 커밋된 줄을 다시 읽어 봐야 알 수 있어, 테스트 트랜잭션 없이 커밋하고 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent_grade_history;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AgentGradeHistoryIntegrationTest {
    private static final String DEMO_SEED = "db/seed/agent-grade-demo.sql";
    private static final String COUNTS_CHECK = "ck_agent_grade_history_counts";
    private static final String RATING_CHECK = "ck_agent_grade_history_rating";
    private static final String SCORES_CHECK = "ck_agent_grade_history_scores";

    @Autowired
    private AgentGradeService agentGradeService;

    @Autowired
    private AgentGradeMetricWriter agentGradeMetricWriter;

    @Autowired
    private AgentGradeHistoryRepository agentGradeHistoryRepository;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        agentGradeHistoryRepository.deleteAllInBatch();
        agentGradeMetricRepository.deleteAllInBatch();
    }

    @Test
    void 시연_시드를_넣은_에이전트는_Tier_2로_매기고_그때의_재료와_지표와_점수를_남긴다() throws IOException {
        AgentId agent = AgentId.newId();
        applyDemoSeed(agent);

        AgentGrade grade = agentGradeService.grade(agent);

        AgentGradeHistoryEntity history = onlyHistory();

        assertThat(grade.tier()).isEqualTo(Tier.TIER_2);
        assertThat(history.getAgentId()).isEqualTo(agent.value());
        assertThat(history.getSuccessfulRuns()).isEqualTo(120);
        assertThat(history.getSupplierFailures()).isEqualTo(5);
        assertThat(history.getReviewCount()).isEqualTo(25);
        assertThat(history.getAverageRating()).isEqualByComparingTo("4.6");
        assertThat(history.getMeasuredAt()).isNotNull();
        assertThat(history.getRatingScore()).isEqualByComparingTo("82.142857");
        assertThat(history.getUsageScore()).isEqualByComparingTo("69.426179");
        assertThat(history.getSuccessRate()).isEqualByComparingTo("0.96");
        assertThat(history.getScore()).isEqualByComparingTo("71.0150");
        assertThat(history.getScoreTier()).isEqualTo(Tier.TIER_2);
        assertThat(history.getTier()).isEqualTo(Tier.TIER_2);
        assertThat(history.getCreatedAt()).isNotNull();
    }

    @Test
    void 한_번도_세지_않은_신규_에이전트는_Tier_4로_매기고_재료_시점은_비워_둔다() {
        AgentId agent = AgentId.newId();

        agentGradeService.grade(agent);

        AgentGradeHistoryEntity history = onlyHistory();

        assertThat(history.getSuccessfulRuns()).isZero();
        assertThat(history.getReviewCount()).isZero();
        assertThat(history.getAverageRating()).isNull();
        assertThat(history.getMeasuredAt()).isNull();
        assertThat(history.getScore()).isEqualByComparingTo("37.5");
        assertThat(history.getScoreTier()).isEqualTo(Tier.TIER_4);
        assertThat(history.getTier()).isEqualTo(Tier.TIER_4);
    }

    // 사용량이 가득 차 점수는 Gold 구간(77.5)이지만 평가가 없어 Gold 허들(20회)에 걸린다.
    @Test
    void 허들에_걸려_내려간_등급은_점수로_닿은_등급과_함께_남긴다() {
        AgentId agent = AgentId.newId();
        new JdbcTemplate(dataSource).update("INSERT INTO agent_grade_metric (agent_id, successful_runs, "
                + "supplier_failures, review_count, average_rating, measured_at) "
                + "VALUES (?, 999, 0, 0, null, CURRENT_TIMESTAMP(6))", agent.value().toString());

        agentGradeService.grade(agent);

        AgentGradeHistoryEntity history = onlyHistory();

        assertThat(history.getScore()).isEqualByComparingTo("77.5");
        assertThat(history.getScoreTier()).isEqualTo(Tier.TIER_2);
        assertThat(history.getTier()).isEqualTo(Tier.TIER_3);
    }

    @Test
    void 매길_때마다_한_줄씩_쌓고_앞의_줄은_고치지_않는다() throws IOException {
        AgentId agent = AgentId.newId();
        applyDemoSeed(agent);
        agentGradeService.grade(agent);

        count(Set.of(), Set.of(agent));
        agentGradeService.grade(agent);

        List<AgentGradeHistoryEntity> histories = agentGradeHistoryRepository.findAll(Sort.by("id"));

        assertThat(histories).hasSize(2);
        assertThat(histories.get(0).getSupplierFailures()).isEqualTo(5);
        assertThat(histories.get(0).getScore()).isEqualByComparingTo("71.0150");
        assertThat(histories.get(1).getSupplierFailures()).isEqualTo(6);
        assertThat(histories.get(1).getScore()).isLessThan(histories.get(0).getScore());
    }

    // 이력은 손으로 고치는 표가 아니지만, 코드가 잘못된 값을 넘겨도 DB 가 받지 않게 한다.
    // H2 는 제약 위반으로, MySQL 은 일반 오류(3819)로 알리므로 예외 종류가 아니라 어느 제약에 걸렸는지를 본다.
    @Test
    void 수가_음수거나_평가_수와_평점이_어긋나거나_점수가_범위를_벗어나면_DB가_받지_않는다() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 재료 네 칸 · 평점 점수 · 사용량 점수 · 성공률 · 종합 점수 순이다.
        Map<String, String> broken = Map.of(
                "-1, 0, 0, null, 62.5, 0, 1, 37.5", COUNTS_CHECK,
                "0, 0, 3, null, 62.5, 0, 1, 37.5", RATING_CHECK,
                "0, 0, 0, 4.00, 62.5, 0, 1, 37.5", RATING_CHECK,
                "0, 0, 0, null, 100.5, 0, 1, 37.5", SCORES_CHECK,
                "0, 0, 0, null, 62.5, -1, 1, 37.5", SCORES_CHECK,
                "0, 0, 0, null, 62.5, 0, 1.5, 37.5", SCORES_CHECK,
                "0, 0, 0, null, 62.5, 0, 1, 100.5", SCORES_CHECK);

        broken.forEach((values, constraint) -> assertThatExceptionOfType(DataAccessException.class)
                .as(values)
                .isThrownBy(() -> jdbc.update("INSERT INTO agent_grade_history (agent_id, successful_runs, "
                        + "supplier_failures, review_count, average_rating, rating_score, usage_score, success_rate, "
                        + "score, score_tier, tier, created_at) "
                        + "VALUES (?, " + values + ", 'TIER_4', 'TIER_4', CURRENT_TIMESTAMP(6))",
                        AgentId.newId().value().toString()))
                .satisfies(exception -> assertThat(exception.getMostSpecificCause().getMessage())
                        .containsIgnoringCase(constraint)));

        assertThat(agentGradeHistoryRepository.count()).isZero();
    }

    private AgentGradeHistoryEntity onlyHistory() {
        List<AgentGradeHistoryEntity> histories = agentGradeHistoryRepository.findAll();

        assertThat(histories).hasSize(1);

        return histories.get(0);
    }

    private void count(Set<AgentId> succeeded, Set<AgentId> failed) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                agentGradeMetricWriter.countRuns(succeeded, failed));
    }

    // 운영자가 하는 것처럼 스크립트의 <AGENT_ID> 를 실제 ID 로 바꿔 그대로 넣는다.
    private void applyDemoSeed(AgentId agent) throws IOException {
        String script = new ClassPathResource(DEMO_SEED).getContentAsString(StandardCharsets.UTF_8)
                .replace("<AGENT_ID>", agent.value().toString());

        new ResourceDatabasePopulator(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)))
                .execute(dataSource);
    }
}
