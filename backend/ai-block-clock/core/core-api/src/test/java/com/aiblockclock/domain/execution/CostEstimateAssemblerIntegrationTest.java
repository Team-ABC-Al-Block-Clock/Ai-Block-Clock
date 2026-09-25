package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Tag("integration")
@SpringBootTest
@Transactional
class CostEstimateAssemblerIntegrationTest {
    private static final UserId OWNER = UserId.of(9401L);
    private static final UserId SELLER = UserId.of(9402L);

    @Autowired
    private CostEstimateAssembler costEstimateAssembler;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 내_노드만_놓으면_노드마다_원가를_적고_더한다() {
        Workflow workflow = save(OWNER, nodePlacement("n1", OWNER), nodePlacement("n2", OWNER));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.total().value()).isEqualByComparingTo("6.000000");
        assertThat(estimate.items())
                .extracting(item -> item.nodeKey().value(), CostItem::sourceType, CostItem::name)
                .containsExactly(
                        tuple("n1", SourceType.NODE, "시드 노드"),
                        tuple("n2", SourceType.NODE, "시드 노드"));
    }

    @Test
    void 에이전트는_속을_펼치지_않고_박힌_버전의_판매가_한_줄로_적는다() {
        AgentRegistration agent = registerAgent(SELLER);
        Workflow workflow = save(OWNER, nodePlacement("n1", OWNER), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items()).hasSize(2);
        assertThat(estimate.items().get(1).sourceType()).isEqualTo(SourceType.AGENT);
        assertThat(estimate.items().get(1).name()).isEqualTo("분석 에이전트");
        assertThat(estimate.items().get(1).price()).isEqualTo(agent.version().finalPrice());
        assertThat(estimate.total().value()).isEqualByComparingTo("9.000000");
    }

    @Test
    void 같은_에이전트를_두_자리에_놓으면_두_줄로_적고_두_번_더한다() {
        AgentRegistration agent = registerAgent(SELLER);
        Workflow workflow = save(OWNER,
                agentPlacement("n1", agent.agent().getId()), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items()).hasSize(2);
        assertThat(estimate.total().value()).isEqualByComparingTo("12.000000");
    }

    @Test
    void 남의_에이전트는_등급_할증을_붙인_판매가로_적는다() {
        AgentRegistration agent = registerAgent(SELLER);
        promote(agent, Tier.TIER_1);
        Workflow workflow = save(OWNER, nodePlacement("n1", OWNER), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items().get(1).price().value()).isEqualByComparingTo("9.000000");
        assertThat(estimate.items()).extracting(CostItem::priceMultiplier)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ONE, new BigDecimal("1.50"));
    }

    @Test
    void 내_에이전트는_등급이_높아도_할증_없이_원가로_적는다() {
        AgentRegistration agent = registerAgent(OWNER);
        promote(agent, Tier.TIER_1);
        Workflow workflow = save(OWNER, nodePlacement("n1", OWNER), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items().get(1).price().value()).isEqualByComparingTo("6.000000");
        assertThat(estimate.total().value()).isEqualByComparingTo("9.000000");
        assertThat(estimate.items().get(1).priceMultiplier()).isEqualByComparingTo("1");
    }

    @Test
    void 분기가_있으면_실행과_같이_견적도_내지_않는다() {
        NodePlacement branch = new NodePlacement(NodeKey.of("br1"), SourceType.CONTROL, null, null,
                InputSource.defaultSource(), null, ControlType.BRANCH,
                new BranchCondition("n1.competition_level", ConditionOperator.EQ, "high"), null, null, null, null);
        Workflow workflow = workflowService.save(OWNER, "경쟁 강도로 가른다", null,
                List.of(nodePlacement("n1", OWNER), branch, nodePlacement("n2", OWNER), nodePlacement("n3", OWNER)),
                List.of(GraphFixtures.edge("e1", "n1", "br1"),
                        GraphFixtures.branchEdge("e2", "br1", "n2", true),
                        GraphFixtures.branchEdge("e3", "br1", "n3", false)));

        assertThatThrownBy(() -> costEstimateAssembler.assemble(workflow))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.EXECUTION_BRANCH_NOT_SUPPORTED);
    }

    // 노드 두 개라 원가는 3 + 3 = 6 ABC 다. 새 버전은 Tier 4(+0%) 라 판매가도 6 이다.
    private AgentRegistration registerAgent(UserId seller) {
        Workflow source = save(seller, nodePlacement("n1", seller), nodePlacement("n2", seller));

        return agentService.register(seller, source, Revision.initial(), new AgentProfile(
                "분석 에이전트", "성적을 분석한다", "학생별 점수 표를 받아 비교한다", null, null, null));
    }

    // 등급 배치가 아직 없어 모든 버전이 Tier 4 다. 원가와 판매가를 가르려고 테스트에서만 등급을 올린다.
    private void promote(AgentRegistration agent, Tier tier) {
        entityManager.flush();
        entityManager.createNativeQuery("update agent_version set tier = ?1 where id = ?2")
                .setParameter(1, tier.name())
                .setParameter(2, agent.version().getId().value().toString())
                .executeUpdate();
        entityManager.clear();
    }

    private Workflow save(UserId owner, NodePlacement first, NodePlacement second) {
        return workflowService.save(owner, "견적 볼 워크플로우", null,
                List.of(first, second),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private NodePlacement nodePlacement(String key, UserId owner) {
        return GraphFixtures.placement(key, NodeDefinitionSeeder.seed(nodeRepository, owner.value()));
    }

    private static NodePlacement agentPlacement(String key, AgentId agentId) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, null,
                agentId.value(), InputSource.defaultSource(), null, null, null, null, null, null, null);
    }
}
