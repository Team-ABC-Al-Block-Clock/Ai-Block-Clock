package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Tag("integration")
@SpringBootTest
@Transactional
class CostEstimateAssemblerIntegrationTest {
    private static final UserId OWNER = UserId.of(9401L);

    @Autowired
    private CostEstimateAssembler costEstimateAssembler;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Test
    void 내_노드만_놓으면_노드마다_원가를_적고_더한다() {
        Workflow workflow = save(nodePlacement("n1"), nodePlacement("n2"));

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
        AgentRegistration agent = registerAgent();
        Workflow workflow = save(nodePlacement("n1"), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items()).hasSize(2);
        assertThat(estimate.items().get(1).sourceType()).isEqualTo(SourceType.AGENT);
        assertThat(estimate.items().get(1).name()).isEqualTo("분석 에이전트");
        assertThat(estimate.items().get(1).price()).isEqualTo(agent.version().finalPrice());
        assertThat(estimate.total().value()).isEqualByComparingTo("9.000000");
    }

    @Test
    void 같은_에이전트를_두_자리에_놓으면_두_줄로_적고_두_번_더한다() {
        AgentRegistration agent = registerAgent();
        Workflow workflow = save(agentPlacement("n1", agent.agent().getId()), agentPlacement("n2", agent.agent().getId()));

        CostEstimate estimate = costEstimateAssembler.assemble(workflow);

        assertThat(estimate.items()).hasSize(2);
        assertThat(estimate.total().value()).isEqualByComparingTo("12.000000");
    }

    private AgentRegistration registerAgent() {
        Workflow source = save(nodePlacement("n1"), nodePlacement("n2"));

        return agentService.register(OWNER, source, Revision.initial(), new AgentProfile(
                "분석 에이전트", "성적을 분석한다", "학생별 점수 표를 받아 비교한다", null, null, null));
    }

    private Workflow save(NodePlacement first, NodePlacement second) {
        return workflowService.save(OWNER, "견적 볼 워크플로우", null,
                List.of(first, second),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private NodePlacement nodePlacement(String key) {
        return GraphFixtures.placement(key, NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()));
    }

    private static NodePlacement agentPlacement(String key, AgentId agentId) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, null,
                agentId.value(), InputSource.defaultSource(), null);
    }
}
