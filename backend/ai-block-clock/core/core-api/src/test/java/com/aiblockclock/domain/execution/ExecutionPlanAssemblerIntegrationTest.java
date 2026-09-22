package com.aiblockclock.domain.execution;

import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.domain.workflow.GraphId;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionService;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.EdgeKey;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.domain.workflow.GraphWriter;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.domain.workflow.WorkflowWriter;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
                Prompt.of("당신은 학원 성적 데이터 분석가입니다."), AiModel.defaultModel());
        NodeDefinition letter = nodeService.create(OWNER, "가정통신문 작성",
                Prompt.of("당신은 교육 심리 전문가입니다."), AiModel.defaultModel());

        Workflow workflow = workflowService.save(OWNER, "성적 리포트", null,
                List.of(
                        new NodePlacement(NodeKey.of("n1"), SourceType.NODE,
                                analyze.getId().value(), InputSource.USER),
                        new NodePlacement(NodeKey.of("n2"), SourceType.NODE,
                                letter.getId().value(), InputSource.PREV)),
                List.of(new GraphEdge(EdgeKey.of("e1"), NodeKey.of("n1"), NodeKey.of("n2"))));
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

    private GraphId graphIdOf(WorkflowId id) {
        return GraphId.of(workflowRepository.findById(id.value()).orElseThrow().getGraphId());
    }
}
