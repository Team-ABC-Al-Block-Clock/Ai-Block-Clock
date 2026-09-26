package com.aiblockclock.domain.node;

import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.execution.ExecutionPlanAssembler;
import com.aiblockclock.domain.execution.PlannedPrompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class NodeOutputIntegrationTest {
    private static final UserId OWNER = UserId.of(9406L);
    private static final UserId BUYER = UserId.of(9407L);
    private static final NodeOutput MARKET = new NodeOutput(OutputKind.JSON, "biz.market_research@1");
    private static final NodeOutput TEXT = new NodeOutput(OutputKind.TEXT, null);

    @Autowired
    private NodeDefinitionService nodeService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Test
    @DisplayName("노드에 고른 출력 형식을 저장하고 실행할 때 그 단계에 싣는다")
    void carriesChosenOutputIntoThePlan() {
        NodeDefinition node = nodeService.create(OWNER, "시장 조사", Prompt.of("시장을 조사한다"),
                AiModel.defaultModel(), MARKET);

        Workflow placed = workflowService.save(OWNER, "시장 조사만 하는 워크플로우", null,
                List.of(GraphFixtures.placement("n1", node.getId().value())), List.of());

        assertThat(nodeService.get(node.getId(), OWNER).getSpec().output()).isEqualTo(MARKET);
        assertThat(executionPlanAssembler.assemble(placed).prompts()).singleElement()
                .extracting(PlannedPrompt::output)
                .isEqualTo(MARKET);
    }

    @Test
    @DisplayName("노드의 출력 형식을 바꿔도 이미 공개한 버전은 공개 시점의 형식으로 돈다")
    void keepsThePublishedVersionOnItsOwnOutput() {
        NodeDefinition node = nodeService.create(OWNER, "시장 조사", Prompt.of("시장을 조사한다"),
                AiModel.defaultModel(), MARKET);
        Workflow source = workflowService.save(OWNER, "굳힐 워크플로우", null,
                List.of(GraphFixtures.placement("n1", node.getId().value())), List.of());
        AgentRegistration agent = agentService.register(OWNER, source, Revision.initial(),
                new AgentProfile("시장 조사 에이전트", "시장을 조사한다", "경쟁 강도와 가격대를 정리한다",
                        null, null, null));

        nodeService.update(node.getId(), OWNER, "시장 조사", Prompt.of("시장을 조사한다"), AiModel.defaultModel(), TEXT);

        Workflow buyer = workflowService.save(BUYER, "산 에이전트를 놓은 워크플로우", null,
                List.of(new NodePlacement(NodeKey.of("a1"), SourceType.AGENT, null, agent.agent().getId().value(),
                        InputSource.defaultSource(), null, null, null, null, null, null, null)),
                List.of());

        assertThat(executionPlanAssembler.assemble(buyer).prompts()).singleElement()
                .extracting(PlannedPrompt::output)
                .isEqualTo(MARKET);
    }
}
