package com.aiblockclock.domain.agent;

import com.aiblockclock.storage.agent.AgentGradeMetricRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// 시드 스크립트와 CHECK 제약은 실제로 DB 에 넣어 봐야 알 수 있어, 테스트 트랜잭션 없이 커밋하고 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent_grade_metric;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AgentGradeMetricIntegrationTest {
    private static final String DEMO_SEED = "db/seed/agent-grade-demo.sql";
    private static final String COUNTS_CHECK = "ck_agent_grade_metric_counts";
    private static final String RATING_CHECK = "ck_agent_grade_metric_rating";

    @Autowired
    private AgentGradeMetricReader agentGradeMetricReader;

    @Autowired
    private AgentGradeMetricWriter agentGradeMetricWriter;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private AgentGradeProperties agentGradeProperties;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        agentGradeMetricRepository.deleteAllInBatch();
    }

    @Test
    void 한_번도_세지_않은_에이전트는_모두_0인_신규로_읽는다() {
        AgentId agent = AgentId.newId();

        assertThat(agentGradeMetricReader.read(agent)).isEqualTo(AgentGradeMetric.fresh(agent));
    }

    @Test
    void 시연_시드를_넣으면_AI_검산과_같은_지표가_나온다() throws IOException {
        AgentId agent = AgentId.newId();

        applyDemoSeed(agent);

        AgentGradeMetric seeded = agentGradeMetricReader.read(agent);
        AgentGradeIndicators indicators = agentGradeProperties.indicatorsOf(seeded);

        assertThat(seeded.successfulRuns()).isEqualTo(120);
        assertThat(seeded.supplierFailures()).isEqualTo(5);
        assertThat(seeded.reviewCount()).isEqualTo(25);
        assertThat(seeded.averageRating()).isEqualByComparingTo("4.6");
        assertThat(seeded.measuredAt()).isNotNull();
        assertThat(indicators.ratingScore()).isCloseTo(82.142857, within(1e-6));
        assertThat(indicators.usageScore()).isCloseTo(69.426179, within(1e-6));
        assertThat(indicators.successRate()).isEqualTo(0.96);
    }

    @Test
    void 시드를_넣은_뒤에_닫힌_실행은_시드_값_위에_더한다() throws IOException {
        AgentId agent = AgentId.newId();
        applyDemoSeed(agent);

        count(Set.of(agent), Set.of());
        count(Set.of(), Set.of(agent));

        AgentGradeMetric counted = agentGradeMetricReader.read(agent);

        assertThat(counted.successfulRuns()).isEqualTo(121);
        assertThat(counted.supplierFailures()).isEqualTo(6);
        assertThat(counted.reviewCount()).isEqualTo(25);
        assertThat(counted.averageRating()).isEqualByComparingTo("4.6");
    }

    @Test
    void 시드를_다시_넣으면_그_사이에_센_것을_지우고_시드_값으로_되돌린다() throws IOException {
        AgentId agent = AgentId.newId();
        applyDemoSeed(agent);
        count(Set.of(agent), Set.of());

        applyDemoSeed(agent);

        assertThat(agentGradeMetricReader.read(agent).successfulRuns()).isEqualTo(120);
    }

    @Test
    void 처음_세는_에이전트는_줄을_만들고_평가는_없는_채로_둔다() {
        AgentId agent = AgentId.newId();

        count(Set.of(agent), Set.of());

        AgentGradeMetric counted = agentGradeMetricReader.read(agent);

        assertThat(counted.successfulRuns()).isEqualTo(1);
        assertThat(counted.supplierFailures()).isZero();
        assertThat(counted.reviewCount()).isZero();
        assertThat(counted.averageRating()).isNull();
        assertThat(counted.measuredAt()).isNotNull();
    }

    @Test
    void 세지_않은_에이전트의_줄은_건드리지_않는다() {
        AgentId counted = AgentId.newId();
        AgentId untouched = AgentId.newId();
        count(Set.of(untouched), Set.of());

        count(Set.of(counted), Set.of());

        assertThat(agentGradeMetricReader.read(untouched).successfulRuns()).isEqualTo(1);
    }

    @Test
    void 실행을_닫는_트랜잭션_밖에서는_세지_못한다() {
        AgentId agent = AgentId.newId();

        assertThatThrownBy(() -> agentGradeMetricWriter.countRuns(Set.of(agent), Set.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(agentGradeMetricRepository.count()).isZero();
    }

    // 시드는 손으로 넣으므로 잘못 적은 값을 DB 가 받아 주면 지표가 범위를 벗어난다.
    // H2 는 제약 위반으로, MySQL 은 일반 오류(3819)로 알리므로 예외 종류가 아니라 어느 제약에 걸렸는지를 본다.
    @Test
    void 평가_수와_평점이_어긋나거나_평점이_1에서_5를_벗어나거나_수가_음수면_DB가_받지_않는다() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Map<String, String> broken = Map.of(
                "(?, 0, 0, 3, 5.50, CURRENT_TIMESTAMP)", RATING_CHECK,
                "(?, 0, 0, 3, 0.50, CURRENT_TIMESTAMP)", RATING_CHECK,
                "(?, 0, 0, 3, null, CURRENT_TIMESTAMP)", RATING_CHECK,
                "(?, 0, 0, 0, 4.00, CURRENT_TIMESTAMP)", RATING_CHECK,
                "(?, -1, 0, 0, null, CURRENT_TIMESTAMP)", COUNTS_CHECK);

        broken.forEach((values, constraint) -> assertThatExceptionOfType(DataAccessException.class)
                .as(values)
                .isThrownBy(() -> jdbc.update("INSERT INTO agent_grade_metric (agent_id, successful_runs, "
                        + "supplier_failures, review_count, average_rating, measured_at) VALUES " + values,
                        AgentId.newId().value().toString()))
                .satisfies(exception -> assertThat(exception.getMostSpecificCause().getMessage())
                        .containsIgnoringCase(constraint)));

        assertThat(agentGradeMetricRepository.count()).isZero();
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
