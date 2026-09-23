package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
import com.aiblockclock.storage.graph.GraphNodeRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class AgentRegistrationIntegrationTest {
    private static final UserId OWNER = UserId.of(9301L);
    private static final UserId OTHER = UserId.of(9302L);

    @Autowired
    private AgentService agentService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    @Autowired
    private GraphNodeRepository graphNodeRepository;

    private Workflow source;

    @BeforeEach
    void setUp() {
        source = saveWorkflow(
                NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()),
                NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()));
    }

    @Test
    @DisplayName("등록하면 에이전트와 첫 버전이 공개 상태로 함께 들어간다")
    void registersPublishedAgentAndFirstVersion() {
        AgentRegistration registered = register(Revision.initial());

        AgentEntity agent = agentRepository.findById(registered.agent().getId().value()).orElseThrow();
        AgentVersionEntity version =
                agentVersionRepository.findById(registered.version().getId().value()).orElseThrow();

        assertThat(agent.getPublicVersionId()).isEqualTo(version.getId());
        assertThat(agent.getPublicVersionNo()).isEqualTo(1);
        assertThat(version.getVersionNo()).isEqualTo(1);
        assertThat(version.getPublicationState()).isEqualTo(PublicationState.PUBLISHED);
        assertThat(version.getSourceWorkflowId()).isEqualTo(source.getId().value());
        assertThat(version.getSourceRevision()).isEqualTo(Revision.initial().value());
    }

    @Test
    @DisplayName("그래프는 복사본이라 원본 워크플로우와 다른 주소에 같은 노드가 놓인다")
    void copiesGraphUnderItsOwnAddress() {
        AgentRegistration registered = register(Revision.initial());

        UUID sourceGraphId = workflowRepository.findById(source.getId().value()).orElseThrow().getGraphId();
        UUID versionGraphId =
                agentVersionRepository.findById(registered.version().getId().value()).orElseThrow().getGraphId();

        assertThat(versionGraphId).isNotEqualTo(sourceGraphId);
        assertThat(graphNodeRepository.findByGraphIdOrderByIdAsc(versionGraphId))
                .extracting(row -> row.getNodeKey())
                .containsExactlyInAnyOrder("n1", "n2");
    }

    @Test
    @DisplayName("단가는 배치된 노드의 원가를 더한 값이다")
    void pricesBySummingPlacedNodeCosts() {
        AgentRegistration registered = register(Revision.initial());

        assertThat(registered.version().getCost().promptNodeCount()).isEqualTo(2);
        assertThat(registered.version().finalPrice().value()).isEqualByComparingTo("6.000000");
    }

    @Test
    @DisplayName("같은 노드를 두 자리에 놓으면 두 번 센다")
    void countsEveryPlacementOfTheSameNode() {
        UUID sharedNode = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        Workflow repeated = saveWorkflow(sharedNode, sharedNode);

        AgentRegistration registered = agentService.register(OWNER, repeated, Revision.initial(), profile());

        assertThat(registered.version().getCost().promptNodeCount()).isEqualTo(2);
        assertThat(registered.version().finalPrice().value()).isEqualByComparingTo("6.000000");
    }

    @Test
    @DisplayName("안에 놓인 에이전트는 그 버전에 박힌 원가와 노드 수를 더한다")
    void addsCostOfPlacedAgentVersion() {
        AgentRegistration inner = register(Revision.initial());
        Workflow outer = workflowService.save(OWNER, "에이전트를 품은 워크플로우", null,
                List.of(GraphFixtures.placement("n1", NodeDefinitionSeeder.seed(nodeRepository, OWNER.value())),
                        agentPlacement("n2", inner.agent().getId())),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        AgentRegistration registered = agentService.register(OWNER, outer, Revision.initial(), profile());

        assertThat(registered.version().getCost().promptNodeCount()).isEqualTo(3);
        assertThat(registered.version().getCost().baseCost().value()).isEqualByComparingTo("9.000000");
    }

    @Test
    @DisplayName("같은 에이전트를 두 자리에 놓으면 두 번 센다")
    void countsEveryPlacementOfTheSameAgent() {
        AgentRegistration inner = register(Revision.initial());
        Workflow outer = workflowService.save(OWNER, "같은 에이전트를 두 번 놓는다", null,
                List.of(agentPlacement("n1", inner.agent().getId()), agentPlacement("n2", inner.agent().getId())),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        AgentRegistration registered = agentService.register(OWNER, outer, Revision.initial(), profile());

        assertThat(registered.version().getCost().promptNodeCount()).isEqualTo(4);
        assertThat(registered.version().getCost().baseCost().value()).isEqualByComparingTo("12.000000");
    }

    @Test
    @DisplayName("보던 회차가 아니면 굳히지 않는다")
    void rejectsStaleRevision() {
        assertThatThrownBy(() -> register(Revision.of(2L)))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_REVISION_CONFLICT);
    }

    @Test
    @DisplayName("남의 노드가 섞여 있으면 굳히지 않는다")
    void rejectsForeignNode() {
        Workflow mixed = saveWorkflow(
                NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()),
                NodeDefinitionSeeder.seed(nodeRepository, OTHER.value()));

        assertThatThrownBy(() -> agentService.register(OWNER, mixed, Revision.initial(), profile()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    private AgentRegistration register(Revision revision) {
        return agentService.register(OWNER, source, revision, profile());
    }

    private Workflow saveWorkflow(UUID firstSource, UUID secondSource) {
        return workflowService.save(
                OWNER,
                "성적 분석",
                null,
                List.of(GraphFixtures.placement("n1", firstSource), GraphFixtures.placement("n2", secondSource)),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private static NodePlacement agentPlacement(String key, AgentId agentId) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, null,
                agentId.value(), InputSource.defaultSource(), null);
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                "성적표 엑셀을 올리면 분석 리포트가 나옵니다", "무엇을 분석할까요?", null);
    }
}
