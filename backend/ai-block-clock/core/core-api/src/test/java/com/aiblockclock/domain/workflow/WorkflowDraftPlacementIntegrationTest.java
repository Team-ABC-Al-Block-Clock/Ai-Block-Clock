package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionEntity;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class WorkflowDraftPlacementIntegrationTest {
    private static final UserId OWNER = UserId.of(9104L);

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private WorkflowWriter workflowWriter;

    @Autowired
    private GraphWriter graphWriter;

    private final List<WorkflowId> saved = new ArrayList<>();

    private UUID seededNode;

    @BeforeEach
    void setUp() {
        seededNode = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
    }

    @AfterEach
    void tearDown() {
        saved.forEach(id -> {
            graphWriter.removeAll(GraphId.of(workflowRepository.findById(id.value()).orElseThrow().getGraphId()));
            workflowWriter.remove(id);
        });
        nodeRepository.deleteAll(nodeRepository.findAllById(placedNodeIds()));
    }

    @Test
    @DisplayName("본문을 실은 배치는 노드를 만들어 놓고, 그래프가 그 노드를 가리킨다")
    void createsCarriedNodeAndPlacesIt() {
        Workflow workflow = save(List.of(existing("n1"), draft("n2", "가정통신문 작성")));

        UUID created = referenceOf(workflow, "n2");

        assertThat(created).isNotEqualTo(seededNode);
        assertThat(nodeRepository.findById(created).orElseThrow())
                .extracting(NodeDefinitionEntity::getName, NodeDefinitionEntity::getOwnerId)
                .containsExactly("가정통신문 작성", OWNER.value());
    }

    @Test
    @DisplayName("같은 본문을 두 자리에 실으면 노드도 둘이 만들어진다")
    void createsOneNodePerCarriedBody() {
        Workflow workflow = save(List.of(draft("n1", "가정통신문 작성"), draft("n2", "가정통신문 작성")));

        assertThat(referenceOf(workflow, "n1")).isNotEqualTo(referenceOf(workflow, "n2"));
    }

    @Test
    @DisplayName("저장이 실패하면 본문으로 만들던 노드도 남지 않는다")
    void leavesNoNodeBehindWhenSaveFails() {
        long before = nodeRepository.count();

        assertThatThrownBy(() -> workflowService.save(OWNER, "순환", null,
                List.of(draft("n1", "가정통신문 작성"), draft("n2", "성적 분석")),
                List.of(GraphFixtures.edge("e1", "n1", "n2"), GraphFixtures.edge("e2", "n2", "n1"))))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_GRAPH_CYCLE);

        assertThat(nodeRepository.count())
                .as("워크플로우가 저장되지 않았으니 노드도 카탈로그에 남으면 안 된다")
                .isEqualTo(before);
    }

    private Workflow save(List<NodePlacement> nodes) {
        Workflow workflow = workflowService.save(OWNER, "새 단계", null, nodes,
                List.of(GraphFixtures.edge("e1", "n1", "n2")));
        saved.add(workflow.getId());

        return workflow;
    }

    private NodePlacement existing(String key) {
        return GraphFixtures.placement(key, seededNode);
    }

    private static NodePlacement draft(String key, String name) {
        return new NodePlacement(NodeKey.of(key), SourceType.NODE, NodeType.LLM, null,
                InputSource.defaultSource(), new NodeDraft(name, "프롬프트 원문", AiModel.defaultModel(), OutputKind.TEXT, null),
                null, null, null, null, null, null);
    }

    private static UUID referenceOf(Workflow workflow, String key) {
        return workflow.getGraph().nodesInOrder().stream()
                .filter(node -> node.key().value().equals(key))
                .findFirst()
                .orElseThrow()
                .reference()
                .referenceId();
    }

    private List<UUID> placedNodeIds() {
        return nodeRepository.findAll().stream()
                .filter(node -> node.getOwnerId() == OWNER.value())
                .map(NodeDefinitionEntity::getId)
                .toList();
    }
}
