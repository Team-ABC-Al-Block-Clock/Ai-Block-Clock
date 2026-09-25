package com.aiblockclock.domain.node;

import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentRegistration;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.execution.ExecutionPlan;
import com.aiblockclock.domain.execution.ExecutionPlanAssembler;
import com.aiblockclock.domain.execution.PlannedPrompt;
import com.aiblockclock.domain.file.UploadedFile;
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
import com.aiblockclock.support.error.CoreException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
@Transactional
class RefFileFreezeIntegrationTest {
    private static final UserId OWNER = UserId.of(9404L);
    private static final UserId STRANGER = UserId.of(9405L);

    @TempDir
    static Path fileRoot;

    @Autowired
    private NodeDefinitionService nodeService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private ExecutionPlanAssembler executionPlanAssembler;

    private NodeDefinition node;

    @DynamicPropertySource
    static void 파일은_임시_폴더에_쌓는다(DynamicPropertyRegistry registry) {
        registry.add("file-store.root", () -> fileRoot);
    }

    @BeforeEach
    void 자료를_붙인_노드를_하나_둔다() {
        node = nodeService.create(OWNER, "성적 분석", Prompt.of("기준표대로 채점한다"), AiModel.defaultModel());
        nodeService.attachRefFile(node.getId(), OWNER, upload("채점 기준표.pdf", "1등급은 90점"));
    }

    @Test
    @DisplayName("붙인 자료는 그 노드를 실행할 때 따라간다")
    void carriesRefFilesIntoThePlan() {
        Workflow placed = workflowService.save(OWNER, "내 노드만 쓴 워크플로우", null,
                List.of(GraphFixtures.placement("n1", node.getId().value())), List.of());

        ExecutionPlan plan = executionPlanAssembler.assemble(placed);

        assertThat(plan.prompts()).singleElement()
                .extracting(PlannedPrompt::refFiles)
                .asInstanceOf(list(RefFile.class))
                .extracting(RefFile::filename)
                .containsExactly("채점 기준표.pdf");
    }

    @Test
    @DisplayName("자료를 바꿔도 이미 공개한 버전은 공개 시점의 자료를 본다")
    void keepsThePublishedVersionOnItsOwnRefFiles() {
        AgentRegistration agent = publishAgent();

        nodeService.detachRefFile(node.getId(), OWNER, attachedId());
        nodeService.attachRefFile(node.getId(), OWNER, upload("새 기준표.pdf", "1등급은 95점"));

        Workflow buyer = workflowService.save(STRANGER, "산 에이전트를 놓은 워크플로우", null,
                List.of(agentPlacement(agent)), List.of());

        assertThat(executionPlanAssembler.assemble(buyer).prompts()).singleElement()
                .extracting(PlannedPrompt::refFiles)
                .asInstanceOf(list(RefFile.class))
                .extracting(RefFile::filename)
                .containsExactly("채점 기준표.pdf");
    }

    @Test
    @DisplayName("남의 노드에는 자료를 붙일 수 없다")
    void refusesToAttachToSomeoneElsesNode() {
        assertThatThrownBy(() -> nodeService.attachRefFile(node.getId(), STRANGER, upload("표.csv", "값")))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("뗀 자료는 목록에서 사라진다")
    void dropsDetachedRefFiles() {
        nodeService.detachRefFile(node.getId(), OWNER, attachedId());

        assertThat(nodeService.getRefFiles(node.getId(), OWNER)).isEmpty();
    }

    @Test
    @DisplayName("붙인 순서대로 돌려준다")
    void keepsTheOrderTheyWereAttachedIn() {
        nodeService.attachRefFile(node.getId(), OWNER, upload("양식.md", "제목"));
        nodeService.attachRefFile(node.getId(), OWNER, upload("예시.csv", "값"));

        assertThat(nodeService.getRefFiles(node.getId(), OWNER)).extracting(RefFile::filename)
                .containsExactly("채점 기준표.pdf", "양식.md", "예시.csv");
    }

    private AgentRegistration publishAgent() {
        Workflow source = workflowService.save(OWNER, "굳힐 워크플로우", null,
                List.of(GraphFixtures.placement("n1", node.getId().value())), List.of());

        return agentService.register(OWNER, source, Revision.initial(),
                new AgentProfile("채점 에이전트", "기준표대로 채점한다", "기준표를 보고 점수를 매긴다",
                        null, null, null));
    }

    private RefFileId attachedId() {
        return nodeService.getRefFiles(node.getId(), OWNER).get(0).id();
    }

    private static NodePlacement agentPlacement(AgentRegistration agent) {
        return new NodePlacement(NodeKey.of("a1"), SourceType.AGENT, null,
                agent.agent().getId().value(), InputSource.defaultSource(), null, null, null, null, null, null, null);
    }

    private static UploadedFile upload(String filename, String content) {
        return new UploadedFile(filename, content.getBytes(StandardCharsets.UTF_8));
    }
}
