package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.storage.agent.AgentEntity;
import com.aiblockclock.storage.agent.AgentRepository;
import com.aiblockclock.storage.agent.AgentVersionEntity;
import com.aiblockclock.storage.agent.AgentVersionRepository;
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
class AgentUpdateIntegrationTest {
    private static final UserId OWNER = UserId.of(9401L);
    private static final UserId OTHER = UserId.of(9402L);

    @Autowired
    private AgentService agentService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentVersionRepository agentVersionRepository;

    private UUID firstNode;

    private UUID secondNode;

    private Workflow frozen;

    private AgentRegistration registered;

    @BeforeEach
    void 에이전트를_하나_굳혀_둔다() {
        firstNode = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        secondNode = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        frozen = twoNodeWorkflow(firstNode, secondNode);

        registered = agentService.register(OWNER, frozen, Revision.initial(), profile("성적 분석 에이전트"));
    }

    @Test
    @DisplayName("굳힌 원본을 고쳐 회차가 오르면 버전도 하나 오른다")
    void raisesVersionWhenTheSourceIsRevised() {
        Workflow revised = replaceWith(NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()));

        AgentRegistration updated = update(revised, profile("성적 분석 에이전트"));

        assertThat(updated.version().getNumber().value()).isEqualTo(2);
        assertThat(updated.version().getSource().revision()).isEqualTo(Revision.of(2L));

        AgentEntity agent = agentRepository.findById(agentId()).orElseThrow();
        assertThat(agent.getPublicVersionNo()).isEqualTo(2);
        assertThat(agent.getPublicVersionId()).isEqualTo(updated.version().getId().value());
    }

    @Test
    @DisplayName("다른 워크플로우를 실어 보내도 버전이 오른다")
    void raisesVersionWhenAnotherWorkflowArrives() {
        Workflow another = twoNodeWorkflow(secondNode, firstNode);

        AgentRegistration updated = update(another, profile("성적 분석 에이전트"));

        assertThat(updated.version().getNumber().value()).isEqualTo(2);
        assertThat(updated.version().getSource().workflowId()).isEqualTo(another.getId());
    }

    @Test
    @DisplayName("굳힌 그대로면 이름만 바뀌고 버전은 그대로다")
    void keepsVersionWhenNothingButTheProfileChanges() {
        AgentRegistration updated = update(frozen, profile("이름만 바꾼 에이전트"));

        assertThat(updated.version().getNumber().value()).isEqualTo(1);
        assertThat(updated.version().getId()).isEqualTo(registered.version().getId());

        AgentEntity agent = agentRepository.findById(agentId()).orElseThrow();
        assertThat(agent.getName()).isEqualTo("이름만 바꾼 에이전트");
        assertThat(agent.getPublicVersionNo()).isEqualTo(1);
        assertThat(agentVersionRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("앞 버전은 지워지지 않고 제 그래프를 그대로 들고 남는다")
    void keepsThePreviousVersion() {
        Workflow revised = replaceWith(NodeDefinitionSeeder.seed(nodeRepository, OWNER.value()));

        AgentRegistration updated = update(revised, profile("성적 분석 에이전트"));

        AgentVersionEntity previous =
                agentVersionRepository.findById(registered.version().getId().value()).orElseThrow();
        AgentVersionEntity current =
                agentVersionRepository.findById(updated.version().getId().value()).orElseThrow();

        assertThat(previous.getVersionNo()).isEqualTo(1);
        assertThat(previous.getGraphId()).isNotEqualTo(current.getGraphId());
    }

    @Test
    @DisplayName("단가는 새 버전의 노드로 다시 매겨진다")
    void repricesOnTheNewVersion() {
        UUID third = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        Workflow longer = workflowService.replace(
                frozen.getId(), OWNER, "한 자리 늘렸다", frozen.getRevision(),
                List.of(GraphFixtures.placement("n1", firstNode),
                        GraphFixtures.placement("n2", secondNode),
                        GraphFixtures.placement("n3", third)),
                List.of(GraphFixtures.edge("e1", "n1", "n2"), GraphFixtures.edge("e2", "n2", "n3")));

        AgentRegistration updated = update(longer, profile("성적 분석 에이전트"));

        assertThat(updated.version().getCost().promptNodeCount()).isEqualTo(3);
        assertThat(updated.version().finalPrice().value()).isEqualByComparingTo("9.000000");
    }

    @Test
    @DisplayName("남의 에이전트는 고칠 수 없다")
    void rejectsOtherOwnersAgent() {
        assertThatThrownBy(() -> agentService.update(
                OTHER, registered.agent().getId(), frozen, frozen.getRevision(), profile("남이 고친다")))
                .isInstanceOf(CoreException.class)
                .extracting(exception -> ((CoreException) exception).getErrorType())
                .isEqualTo(ErrorType.AGENT_FORBIDDEN);
    }

    @Test
    @DisplayName("없는 에이전트는 고칠 수 없다")
    void rejectsUnknownAgent() {
        assertThatThrownBy(() -> agentService.update(
                OWNER, AgentId.newId(), frozen, frozen.getRevision(), profile("없는 것을 고친다")))
                .isInstanceOf(CoreException.class)
                .extracting(exception -> ((CoreException) exception).getErrorType())
                .isEqualTo(ErrorType.AGENT_NOT_FOUND);
    }

    @Test
    @DisplayName("보던 회차가 아니면 고치지 않는다")
    void rejectsStaleRevision() {
        assertThatThrownBy(() -> agentService.update(
                OWNER, registered.agent().getId(), frozen, Revision.of(99L), profile("성적 분석 에이전트")))
                .isInstanceOf(CoreException.class)
                .extracting(exception -> ((CoreException) exception).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_REVISION_CONFLICT);
    }

    @Test
    @DisplayName("남의 노드가 섞이면 고치지 않는다")
    void rejectsForeignNode() {
        Workflow mixed = twoNodeWorkflow(firstNode, NodeDefinitionSeeder.seed(nodeRepository, OTHER.value()));

        assertThatThrownBy(() -> update(mixed, profile("성적 분석 에이전트")))
                .isInstanceOf(CoreException.class)
                .extracting(exception -> ((CoreException) exception).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    private AgentRegistration update(Workflow source, AgentProfile profile) {
        return agentService.update(OWNER, registered.agent().getId(), source, source.getRevision(), profile);
    }

    private Workflow replaceWith(UUID replacingSecondNode) {
        return workflowService.replace(
                frozen.getId(), OWNER, "고친 원본", frozen.getRevision(),
                List.of(GraphFixtures.placement("n1", firstNode),
                        GraphFixtures.placement("n2", replacingSecondNode)),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private Workflow twoNodeWorkflow(UUID first, UUID second) {
        return workflowService.save(
                OWNER, "성적 분석", null,
                List.of(GraphFixtures.placement("n1", first), GraphFixtures.placement("n2", second)),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
    }

    private UUID agentId() {
        return registered.agent().getId().value();
    }

    private static AgentProfile profile(String name) {
        return new AgentProfile(name, "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                "성적표 엑셀을 올리면 분석 리포트가 나옵니다", "무엇을 분석할까요?", null);
    }
}
