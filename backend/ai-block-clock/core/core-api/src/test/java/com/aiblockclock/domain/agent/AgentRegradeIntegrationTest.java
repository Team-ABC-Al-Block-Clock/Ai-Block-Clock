package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.execution.Execution;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.domain.execution.ExecutionPlanAssembler;
import com.aiblockclock.domain.execution.ExecutionRequestId;
import com.aiblockclock.domain.execution.ExecutionReservationManager;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.enums.payment.LedgerAccountType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.agent.AgentGradeHistoryEntity;
import com.aiblockclock.storage.agent.AgentGradeHistoryRepository;
import com.aiblockclock.storage.agent.AgentGradeMetricRepository;
import com.aiblockclock.storage.agent.AgentTierChangeEntity;
import com.aiblockclock.storage.agent.AgentTierChangeRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.execution.ExecutionChargeRateEntity;
import com.aiblockclock.storage.execution.ExecutionChargeRateRepository;
import com.aiblockclock.storage.execution.ExecutionRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.payment.LedgerAccountEntity;
import com.aiblockclock.storage.payment.LedgerAccountRepository;
import com.aiblockclock.storage.payment.LedgerEntryRepository;
import com.aiblockclock.storage.payment.LedgerTransactionRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

// 재산정(PAY-022)이 에이전트의 모든 버전 가격 등급을 바꾸고, 바뀐 것만 남기는지 본다.
// 선차감은 제 트랜잭션을 열고 커밋해야 배율이 남아서, 테스트 트랜잭션 없이 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:agent_regrade;MODE=MySQL;DB_CLOSE_DELAY=-1")
class AgentRegradeIntegrationTest {
    private static final UserId SELLER = UserId.of(9701L);
    private static final UserId BUYER = UserId.of(9702L);
    private static final String CHANGED_CHECK = "ck_agent_tier_change_changed";
    private static final String PRICES_CHECK = "ck_agent_tier_change_prices";

    @Autowired
    private AgentGradeService agentGradeService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Autowired
    private AgentTierChangeRepository agentTierChangeRepository;

    @Autowired
    private AgentGradeHistoryRepository agentGradeHistoryRepository;

    @Autowired
    private AgentGradeMetricRepository agentGradeMetricRepository;

    @Autowired
    private ExecutionReservationManager executionReservationManager;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Autowired
    private ExecutionRepository executionRepository;

    @Autowired
    private ExecutionChargeRateRepository executionChargeRateRepository;

    @Autowired
    private LedgerAccountRepository ledgerAccountRepository;

    @Autowired
    private LedgerTransactionRepository ledgerTransactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    private Workflow source;

    private AgentRegistration registered;

    // 노드 두 개라 원가는 3 + 3 = 6 ABC 다. 버전을 하나 더 내서 v1 · v2 두 줄을 둔다.
    @BeforeEach
    void 버전이_둘인_에이전트를_굳혀_둔다() {
        source = workflowService.save(SELLER, "굳힐 워크플로우", null,
                List.of(sellerNode("n1"), sellerNode("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        registered = agentService.register(SELLER, source, Revision.initial(), profile());
        publishNextVersion();
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        ledgerTransactionRepository.deleteAllInBatch();
        ledgerAccountRepository.deleteAllInBatch();
        executionChargeRateRepository.deleteAllInBatch();
        executionRepository.deleteAllInBatch();
        agentTierChangeRepository.deleteAllInBatch();
        agentGradeHistoryRepository.deleteAllInBatch();
        agentGradeMetricRepository.deleteAllInBatch();
    }

    // 시드 재료는 AI 검산과 같이 71.0150 · Tier 2 다. 판매가는 6 × 1.3 = 7.8 이다.
    @Test
    void 다시_매기면_모든_버전이_같은_등급이_되고_바뀐_버전마다_전후_등급과_판매가를_남긴다() {
        putMetric(120, 5, 25, "4.6");

        AgentGrade grade = agentGradeService.regrade(agentId());

        assertThat(grade.tier()).isEqualTo(Tier.TIER_2);
        assertThat(versions()).extracting(AgentVersionEntity::getVersionNo, AgentVersionEntity::getTier)
                .containsExactly(tuple(1, Tier.TIER_2), tuple(2, Tier.TIER_2));

        long historyId = onlyHistory().getId();
        assertThat(changes())
                .extracting(AgentTierChangeEntity::getBeforeTier, AgentTierChangeEntity::getAfterTier,
                        change -> change.getBeforePrice().stripTrailingZeros().toPlainString(),
                        change -> change.getAfterPrice().stripTrailingZeros().toPlainString(),
                        AgentTierChangeEntity::getGradeHistoryId)
                .containsExactly(
                        tuple(Tier.TIER_4, Tier.TIER_2, "6", "7.8", historyId),
                        tuple(Tier.TIER_4, Tier.TIER_2, "6", "7.8", historyId));
        assertThat(changes()).extracting(AgentTierChangeEntity::getAgentVersionId)
                .containsExactlyElementsOf(versions().stream().map(AgentVersionEntity::getId).toList());
        assertThat(changes()).allSatisfy(change -> assertThat(change.getCreatedAt()).isNotNull());
    }

    @Test
    void 그사이_실행이_없어_같은_결과면_몇_번을_다시_매겨도_이력도_변경도_늘지_않는다() {
        putMetric(120, 5, 25, "4.6");

        agentGradeService.regrade(agentId());
        agentGradeService.regrade(agentId());
        agentGradeService.regrade(agentId());

        assertThat(histories()).hasSize(1);
        assertThat(changes()).hasSize(2);
    }

    @Test
    void 신규_에이전트는_이미_Tier_4라_이력만_남기고_변경은_남기지_않는다() {
        AgentGrade grade = agentGradeService.regrade(agentId());

        assertThat(grade.tier()).isEqualTo(Tier.TIER_4);
        assertThat(histories()).hasSize(1);
        assertThat(changes()).isEmpty();
    }

    // 공급자 탓 실패가 쌓여 성공률이 120 / 170 ≈ 0.71 이 되면 점수가 크게 깎여 Tier 4 로 내려간다.
    @Test
    void 성공률이_떨어져_등급이_내려가면_내려간_등급의_판매가로_바꾸고_그_근거인_새_이력을_가리킨다() {
        putMetric(120, 5, 25, "4.6");
        agentGradeService.regrade(agentId());

        putMetric(120, 50, 25, "4.6");
        AgentGrade lowered = agentGradeService.regrade(agentId());

        assertThat(lowered.tier()).isEqualTo(Tier.TIER_4);
        assertThat(versions()).extracting(AgentVersionEntity::getTier).containsOnly(Tier.TIER_4);

        List<AgentGradeHistoryEntity> histories = histories();
        assertThat(histories).hasSize(2);
        assertThat(changes()).hasSize(4);
        assertThat(changes().subList(2, 4))
                .extracting(AgentTierChangeEntity::getBeforeTier, AgentTierChangeEntity::getAfterTier,
                        change -> change.getAfterPrice().stripTrailingZeros().toPlainString(),
                        AgentTierChangeEntity::getGradeHistoryId)
                .containsOnly(tuple(Tier.TIER_2, Tier.TIER_4, "6", histories.get(1).getId()));
    }

    @Test
    void 다시_매긴_뒤에_낸_새_버전은_바뀐_등급으로_시작해_다음_재산정에서_바꿀_것이_없다() {
        putMetric(120, 5, 25, "4.6");
        agentGradeService.regrade(agentId());

        publishNextVersion();

        assertThat(versions()).extracting(AgentVersionEntity::getVersionNo, AgentVersionEntity::getTier)
                .containsExactly(tuple(1, Tier.TIER_2), tuple(2, Tier.TIER_2), tuple(3, Tier.TIER_2));

        agentGradeService.regrade(agentId());

        assertThat(changes()).hasSize(2);
    }

    // 접수할 때 단계마다 배율을 적고 고치지 않는다. 그래서 등급을 바꾼 뒤에도 이미 접수한 실행은 옛 배율로 정산하고,
    // 바꾼 뒤 접수한 실행부터 새 배율이다.
    @Test
    void 이미_접수한_실행은_옛_배율을_지키고_다시_매긴_뒤_접수한_실행부터_새_배율이다() {
        Workflow bought = workflowService.save(BUYER, "산 에이전트를 놓았다", null,
                List.of(agentPlacement("a1")), List.of());
        charge("100");

        Execution before = reserve(bought);
        putMetric(120, 5, 25, "4.6");
        agentGradeService.regrade(agentId());
        Execution after = reserve(bought);

        assertThat(multipliersOf(before)).containsOnly("1");
        assertThat(multipliersOf(after)).containsOnly("1.3");
    }

    // 변경 표는 손으로 고치는 표가 아니지만, 코드가 잘못된 값을 넘겨도 DB 가 받지 않게 한다.
    @Test
    void 등급이_그대로이거나_판매가가_0_이하면_DB가_받지_않는다() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // 바뀌기 전 등급 · 뒤 등급 · 전 판매가 · 뒤 판매가 순이다.
        Map<String, String> broken = Map.of(
                "'TIER_2', 'TIER_2', 7.8, 7.8", CHANGED_CHECK,
                "'TIER_4', 'TIER_2', 0, 7.8", PRICES_CHECK,
                "'TIER_4', 'TIER_2', 6, -1", PRICES_CHECK);

        broken.forEach((values, constraint) -> assertThatExceptionOfType(DataAccessException.class)
                .as(values)
                .isThrownBy(() -> jdbc.update("INSERT INTO agent_tier_change (agent_id, agent_version_id, "
                        + "grade_history_id, before_tier, after_tier, before_price, after_price, created_at) "
                        + "VALUES (?, ?, 1, " + values + ", CURRENT_TIMESTAMP(6))",
                        UUID.randomUUID().toString(), UUID.randomUUID().toString()))
                .satisfies(exception -> assertThat(exception.getMostSpecificCause().getMessage())
                        .containsIgnoringCase(constraint)));

        assertThat(agentTierChangeRepository.count()).isZero();
    }

    private AgentId agentId() {
        return registered.agent().getId();
    }

    // 원본을 고쳐 회차를 올린 뒤 다시 등록하면 버전이 하나 오른다.
    private void publishNextVersion() {
        source = workflowService.replace(source.getId(), SELLER, "고친 워크플로우", source.getRevision(),
                List.of(sellerNode("n1"), sellerNode("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        agentService.update(SELLER, agentId(), source, source.getRevision(), profile());
    }

    // 재료는 실행이 닫힐 때 쌓이지만, 여기서는 원하는 재료를 바로 넣어 둔다. 시연 시드를 넣는 것과 같다.
    private void putMetric(long successes, long failures, int reviews, String rating) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String agent = agentId().value().toString();

        jdbc.update("DELETE FROM agent_grade_metric WHERE agent_id = ?", agent);
        jdbc.update("INSERT INTO agent_grade_metric (agent_id, successful_runs, supplier_failures, review_count, "
                        + "average_rating, measured_at) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))",
                agent, successes, failures, reviews, new BigDecimal(rating));
    }

    private List<AgentVersionEntity> versions() {
        return agentVersionRepository.findAllByAgentIdOrderByVersionNoAsc(agentId().value());
    }

    private List<AgentTierChangeEntity> changes() {
        return agentTierChangeRepository.findAll().stream()
                .filter(change -> change.getAgentId().equals(agentId().value()))
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .toList();
    }

    private List<AgentGradeHistoryEntity> histories() {
        return agentGradeHistoryRepository.findAll().stream()
                .filter(history -> history.getAgentId().equals(agentId().value()))
                .sorted((left, right) -> Long.compare(left.getId(), right.getId()))
                .toList();
    }

    private AgentGradeHistoryEntity onlyHistory() {
        List<AgentGradeHistoryEntity> histories = histories();

        assertThat(histories).hasSize(1);

        return histories.get(0);
    }

    private Execution reserve(Workflow workflow) {
        Execution requested = Execution.requested(ExecutionId.newId(), ExecutionRequestId.of(UUID.randomUUID()),
                workflow.getId(), BUYER, 2, null, false);

        return executionReservationManager.reserve(requested, workflow, executionPlanAssembler.assemble(workflow));
    }

    private List<String> multipliersOf(Execution execution) {
        return executionChargeRateRepository.findAllByExecutionId(execution.getId().value()).stream()
                .map(ExecutionChargeRateEntity::getPriceMultiplier)
                .map(multiplier -> multiplier.stripTrailingZeros().toPlainString())
                .toList();
    }

    // 잔액은 운영 코드와 같은 경로(증감 UPDATE)로만 바꾼다.
    private void charge(String amount) {
        LedgerAccountEntity account = ledgerAccountRepository.saveAndFlush(
                LedgerAccountEntity.of(LedgerAccountType.ABC_BALANCE, Long.toString(BUYER.value())));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                ledgerAccountRepository.addBalanceDelta(account.getId(), new BigDecimal(amount), Instant.now()));
    }

    private NodePlacement sellerNode(String key) {
        return GraphFixtures.placement(key, NodeDefinitionSeeder.seed(nodeRepository, SELLER.value()));
    }

    private NodePlacement agentPlacement(String key) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, agentId().value(), InputSource.defaultSource(), null, null, null, null, null, null, null);
    }

    private static AgentProfile profile() {
        return new AgentProfile("분석 에이전트", "성적을 분석한다", "학생별 점수 표를 받아 비교한다", null, null, null);
    }
}
