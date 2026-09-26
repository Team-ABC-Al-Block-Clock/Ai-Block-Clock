package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionService;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.EdgeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.GraphWriter;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.domain.workflow.WorkflowWriter;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.ConditionOperator;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class ExecutionPlanAssemblerIntegrationTest {
    private static final UserId OWNER = UserId.of(9401L);

    @Autowired
    private NodeDefinitionService nodeService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowWriter workflowWriter;

    @Autowired
    private GraphWriter graphWriter;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    @Autowired
    private WorkflowRepository workflowRepository;

    private WorkflowId workflowId;

    // 정리는 삭제 정책을 타지 않는다. WorkflowUsageView 가 실제 조회로 바뀌면
    // workflowService.delete 로 치우던 방식은 정리부터 막힌다.
    @AfterEach
    void tearDown() {
        if (workflowId != null) {
            graphWriter.removeAll(graphIdOf(workflowId));
            workflowWriter.remove(workflowId);
        }
    }

    @Test
    @DisplayName("연결 순서대로 프롬프트를 펴고 배치의 입력 출처를 함께 담는다")
    void flattensInOrder() {
        NodeDefinition analyze = nodeService.create(OWNER, "성적 분석",
                Prompt.of("당신은 학원 성적 데이터 분석가입니다."), AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null));
        NodeDefinition letter = nodeService.create(OWNER, "가정통신문 작성",
                Prompt.of("당신은 교육 심리 전문가입니다."), AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null));

        Workflow workflow = workflowService.save(OWNER, "성적 리포트", null,
                List.of(
                        GraphFixtures.placement("n1", analyze.getId().value(), InputSource.USER),
                        GraphFixtures.placement("n2", letter.getId().value(), InputSource.PREV)),
                List.of(new GraphEdge(EdgeKey.of("e1"), NodeKey.of("n1"), NodeKey.of("n2"), null)));
        workflowId = workflow.getId();

        ExecutionPlan plan = executionPlanAssembler.assemble(workflowService.get(workflowId, OWNER));

        assertThat(plan.promptCount()).isEqualTo(2);
        assertThat(plan.prompts()).extracting(PlannedPrompt::name)
                .containsExactly("성적 분석", "가정통신문 작성");
        assertThat(plan.prompts()).extracting(PlannedPrompt::inputSource)
                .containsExactly(InputSource.USER, InputSource.PREV);
        assertThat(plan.prompts().get(0).prompt().value())
                .isEqualTo("당신은 학원 성적 데이터 분석가입니다.");
    }

    @Test
    @DisplayName("시작 · 끝 노드는 돌릴 프롬프트가 없어 건너뛴다")
    void skipsStartAndEndNodes() {
        NodeDefinition analyze = nodeService.create(OWNER, "성적 분석",
                Prompt.of("당신은 학원 성적 데이터 분석가입니다."), AiModel.defaultModel(),
                new NodeOutput(OutputKind.TEXT, null));

        Workflow workflow = workflowService.save(OWNER, "성적 리포트", null,
                List.of(
                        GraphFixtures.startPlacement("s1", List.of(GraphFixtures.field("brand_name"))),
                        GraphFixtures.placement("n1", analyze.getId().value()),
                        GraphFixtures.endPlacement("e1", ResultShape.DOCUMENT, ResultFormat.PDF)),
                List.of(GraphFixtures.edge("g1", "s1", "n1"), GraphFixtures.edge("g2", "n1", "e1")));
        workflowId = workflow.getId();

        ExecutionPlan plan = executionPlanAssembler.assemble(workflowService.get(workflowId, OWNER));

        assertThat(plan.prompts()).extracting(PlannedPrompt::name).containsExactly("성적 분석");
    }

    @Test
    @DisplayName("분기 노드는 돌릴 프롬프트가 없어 단계로 펴지 않는다")
    void leavesBranchOutOfPrompts() {
        NodeDefinition step = nodeService.create(OWNER, "시장 조사",
                Prompt.of("당신은 시장 조사 분석가입니다."), AiModel.defaultModel(), new NodeOutput(OutputKind.TEXT, null));
        NodePlacement branch = new NodePlacement(NodeKey.of("br1"), SourceType.CONTROL, null,
                InputSource.defaultSource(), null, ControlType.BRANCH,
                new BranchCondition("n1.competition_level", ConditionOperator.EQ, "high"), null, null, null, null);

        Workflow workflow = workflowService.save(OWNER, "경쟁 강도로 가른다", null,
                List.of(
                        GraphFixtures.placement("n1", step.getId().value()),
                        branch,
                        GraphFixtures.placement("n2", step.getId().value()),
                        GraphFixtures.placement("n3", step.getId().value())),
                List.of(GraphFixtures.edge("e1", "n1", "br1"),
                        GraphFixtures.branchEdge("e2", "br1", "n2", true),
                        GraphFixtures.branchEdge("e3", "br1", "n3", false)));
        workflowId = workflow.getId();

        assertThat(executionPlanAssembler.assemble(workflowService.get(workflowId, OWNER)).prompts())
                .extracting(prompt -> prompt.placedKey().value())
                .containsExactly("n1", "n2", "n3");
    }

    private GraphId graphIdOf(WorkflowId id) {
        return GraphId.of(workflowRepository.findById(id.value()).orElseThrow().getGraphId());
    }
}
