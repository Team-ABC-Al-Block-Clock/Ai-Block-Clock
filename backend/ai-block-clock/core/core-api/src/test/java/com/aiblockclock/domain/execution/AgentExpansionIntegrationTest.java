package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionService;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class AgentExpansionIntegrationTest {
    private static final UserId OWNER = UserId.of(9403L);

    @Autowired
    private NodeDefinitionService nodeService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    private AgentRegistration agent;

    private NodeDefinition soldNode;

    @BeforeEach
    void 프롬프트_둘짜리_에이전트를_굳혀_둔다() {
        soldNode = nodeService.create(OWNER, "성적 분석", Prompt.of("점수를 비교한다"), AiModel.defaultModel());

        Workflow source = workflowService.save(OWNER, "굳힐 워크플로우", null,
                List.of(GraphFixtures.placement("n1", soldNode.getId().value()),
                        node("n2", "가정통신문 작성", "가정통신문을 쓴다")),
                List.of(GraphFixtures.edge("e1", "n1", "n2")));

        agent = agentService.register(OWNER, source, Revision.initial(), profile());
    }

    @Test
    @DisplayName("에이전트 자리는 그 버전의 프롬프트로 펼쳐진다")
    void expandsAgentPlacementIntoItsOwnPrompts() {
        Workflow placed = workflowService.save(OWNER, "에이전트를 품은 워크플로우", null,
                List.of(agentPlacement("a1"), node("n3", "요약", "결과를 요약한다")),
                List.of(GraphFixtures.edge("e1", "a1", "n3")));

        ExecutionPlan plan = executionPlanAssembler.assemble(placed);

        assertThat(plan.prompts()).extracting(PlannedPrompt::name)
                .containsExactly("성적 분석", "가정통신문 작성", "요약");
    }

    @Test
    @DisplayName("에이전트 하나가 캔버스에서는 한 자리지만 펼치면 프롬프트 수만큼이다")
    void countsPromptsNotCanvasNodes() {
        Workflow placed = workflowService.save(OWNER, "한 자리", null,
                List.of(agentPlacement("a1")), List.of());

        assertThat(placed.getGraph().nodesInOrder()).hasSize(1);
        assertThat(executionPlanAssembler.assemble(placed).promptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("원본 워크플로우를 고쳐도 굳힌 버전의 프롬프트는 그대로다")
    void keepsTheFrozenPromptsWhenTheSourceChanges() {
        Workflow placed = workflowService.save(OWNER, "에이전트를 품었다", null,
                List.of(agentPlacement("a1")), List.of());

        Workflow source = workflowService.get(agent.version().getSource().workflowId(), OWNER);
        workflowService.replace(source.getId(), OWNER, "바뀐 원본", source.getRevision(),
                List.of(node("n1", "완전히 다른 단계", "다른 일을 한다")), List.of());

        assertThat(executionPlanAssembler.assemble(placed).prompts())
                .extracting(PlannedPrompt::name)
                .containsExactly("성적 분석", "가정통신문 작성");
    }

    @Test
    @DisplayName("굳힌 뒤 노드를 고쳐도 판 버전은 판 내용을 그대로 펼친다")
    void keepsTheSoldPromptsWhenTheNodeIsEdited() {
        Workflow placed = workflowService.save(OWNER, "에이전트를 품었다", null,
                List.of(agentPlacement("a1")), List.of());

        nodeService.update(soldNode.getId(), OWNER, "완전히 다른 이름",
                Prompt.of("완전히 다른 일을 한다"), AiModel.defaultModel());

        assertThat(executionPlanAssembler.assemble(placed).prompts())
                .extracting(PlannedPrompt::name, prompt -> prompt.prompt().value())
                .containsExactly(
                        tuple("성적 분석", "점수를 비교한다"),
                        tuple("가정통신문 작성", "가정통신문을 쓴다"));
    }

    @Test
    @DisplayName("캔버스에 그냥 놓은 노드는 고치면 따라 바뀐다")
    void followsTheLiveNodeOutsideAnyVersion() {
        Workflow placed = workflowService.save(OWNER, "노드만 놓았다", null,
                List.of(GraphFixtures.placement("n1", soldNode.getId().value())), List.of());

        nodeService.update(soldNode.getId(), OWNER, "고친 이름",
                Prompt.of("고친 일을 한다"), AiModel.defaultModel());

        assertThat(executionPlanAssembler.assemble(placed).prompts())
                .extracting(PlannedPrompt::name, prompt -> prompt.prompt().value())
                .containsExactly(tuple("고친 이름", "고친 일을 한다"));
    }

    @Test
    @DisplayName("굳힌 에이전트를 남이 놓고 돌려도 주인은 원 제작자 그대로다")
    void keepsTheAgentAuthorAsOwnerForSomeoneElsesRun() {
        UserId buyer = UserId.of(5555L);
        Workflow placed = workflowService.save(buyer, "산 에이전트를 놓았다", null,
                List.of(agentPlacement("a1")), List.of());

        assertThat(executionPlanAssembler.assemble(placed).prompts())
                .extracting(PlannedPrompt::owner)
                .containsExactly(OWNER, OWNER);
    }

    @Test
    @DisplayName("캔버스에 그냥 놓은 노드는 그 노드의 주인을 들고 나온다")
    void carriesTheLiveNodeOwner() {
        Workflow placed = workflowService.save(OWNER, "노드만 놓았다", null,
                List.of(GraphFixtures.placement("n1", soldNode.getId().value())), List.of());

        assertThat(executionPlanAssembler.assemble(placed).prompts())
                .extracting(PlannedPrompt::owner)
                .containsExactly(OWNER);
    }

    private NodePlacement node(String key, String name, String prompt) {
        NodeDefinition created = nodeService.create(OWNER, name, Prompt.of(prompt), AiModel.defaultModel());

        return GraphFixtures.placement(key, created.getId().value());
    }

    private NodePlacement agentPlacement(String key) {
        return new NodePlacement(NodeKey.of(key), SourceType.AGENT, null,
                agent.agent().getId().value(), InputSource.defaultSource(), null, null, null);
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 비교한다", null, null, null);
    }
}
