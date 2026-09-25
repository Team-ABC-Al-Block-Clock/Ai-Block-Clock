package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.AgentNode;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.GraphNode;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.PlacedSource;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowDetail;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
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
class AgentPlacementIntegrationTest {
    private static final UserId OWNER = UserId.of(9401L);
    private static final UserId OTHER = UserId.of(9402L);

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    private AgentRegistration published;

    @BeforeEach
    void 에이전트를_하나_굳혀_둔다() {
        published = agentService.register(OWNER, sourceWorkflow(OWNER), Revision.initial(), profile());
    }

    @Test
    @DisplayName("공개된 에이전트를 놓으면 그때 공개돼 있던 버전이 박힌다")
    void pinsThePublicVersionAtPlacement() {
        Workflow placed = workflowService.save(OWNER, "에이전트를 놓는다", null,
                List.of(agentPlacement("n1", published.agent().getId()), nodePlacement("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        GraphNode node = placed.getGraph().nodesInOrder().get(0);

        assertThat(node).isInstanceOf(AgentNode.class);
        assertThat(node.reference().referenceId()).isEqualTo(published.agent().getId().value());
        assertThat(node.reference().versionId()).isEqualTo(published.version().getId().value());
    }

    @Test
    @DisplayName("남의 공개 에이전트도 저장은 된다 — 못 파는 것뿐이다")
    void savesWorkflowHoldingSomeoneElsesAgent() {
        Workflow placed = workflowService.save(OTHER, "남의 것을 놓는다", null,
                List.of(agentPlacement("n1", published.agent().getId()), nodePlacement("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        assertThat(placed.getGraph().nodesInOrder()).hasSize(2);
        assertThatThrownBy(() -> agentService.register(OTHER, placed, Revision.initial(), profile()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    // 상세 조회의 mine 은 화면이 "판매 등록 가능 / 불가" 를 띄우는 근거다.
    // 여기서 막힌 워크플로우가 화면에서는 등록 가능해 보이면 눌렀을 때 403 이 난다.
    @Test
    @DisplayName("등록이 막히는 워크플로우는 상세 조회의 mine 도 전부 true 가 아니다")
    void detailAgreesWithRegistrationPolicy() {
        Workflow placed = workflowService.save(OTHER, "남의 것을 놓는다", null,
                List.of(agentPlacement("n1", published.agent().getId()), nodePlacement("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        WorkflowDetail detail = workflowService.getDetail(placed.getId(), OTHER);

        assertThat(detail.sources().values()).anyMatch(source -> !source.mine());
        assertThat(detail.sourceOf(NodeKey.of("n1")))
                .isEqualTo(new PlacedSource("성적 분석 에이전트", false));
        assertThatThrownBy(() -> agentService.register(OTHER, placed, Revision.initial(), profile()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    @Test
    @DisplayName("전부 내 것이면 mine 이 전부 true 이고 등록도 통과한다")
    void detailMarksEveryOwnPlacementAsMine() {
        Workflow placed = workflowService.save(OWNER, "내 것만 놓는다", null,
                List.of(agentPlacement("n1", published.agent().getId()), nodePlacement("n2")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        WorkflowDetail detail = workflowService.getDetail(placed.getId(), OWNER);

        assertThat(detail.sources().values()).allMatch(PlacedSource::mine);
        assertThat(detail.sourceOf(NodeKey.of("n2")).name()).isEqualTo("시드 노드");
        assertThat(agentService.register(OWNER, placed, Revision.initial(), profile())).isNotNull();
    }

    @Test
    @DisplayName("공개 버전이 없는 에이전트는 놓을 수 없다")
    void rejectsAgentWithoutPublicVersion() {
        assertThatThrownBy(() -> workflowService.save(OWNER, "없는 에이전트", null,
                List.of(agentPlacement("n1", AgentId.newId())),
                List.of()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND);
    }

    private Workflow sourceWorkflow(UserId owner) {
        UUID node = NodeDefinitionSeeder.seed(nodeRepository, owner.value());

        return workflowService.save(owner, "굳힐 워크플로우", null,
                List.of(GraphFixtures.placement("n1", node), GraphFixtures.placement("n2", node)),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private NodePlacement nodePlacement(String key) {
        return GraphFixtures.placement(key, NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()));
    }

    private static NodePlacement agentPlacement(String key, AgentId agentId) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, null,
                agentId.value(), InputSource.defaultSource(), null, null, null, null, null, null, null);
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다", null, null, null);
    }
}
