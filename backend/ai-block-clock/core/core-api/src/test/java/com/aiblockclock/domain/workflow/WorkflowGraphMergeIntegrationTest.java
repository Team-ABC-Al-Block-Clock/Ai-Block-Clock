package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.storage.graph.GraphEdgeEntity;
import com.aiblockclock.storage.graph.GraphNodeEntity;
import com.aiblockclock.storage.graph.GraphEdgeRepository;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.graph.GraphNodeRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class WorkflowGraphMergeIntegrationTest {
    private static final UserId OWNER = UserId.of(9103L);
    private UUID firstSource;
    private UUID secondSource;
    private UUID replacedSource;
    private UUID addedSource;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private GraphNodeRepository graphNodeRepository;

    @Autowired
    private GraphEdgeRepository graphEdgeRepository;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private WorkflowWriter workflowWriter;

    @Autowired
    private GraphNodeWriter graphNodeWriter;

    @Autowired
    private GraphEdgeWriter graphEdgeWriter;

    @Autowired
    private WorkflowRepository workflowRepository;

    private WorkflowId workflowId;

    @BeforeEach
    void setUp() {
        firstSource = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        secondSource = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        replacedSource = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        addedSource = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());

        workflowId = workflowService.save(
                OWNER,
                "그래프 병합",
                null,
                List.of(placement("n1", firstSource), placement("n2", secondSource)),
                List.of(GraphFixtures.edge("e1", "n1", "n2"))).getId();
    }

    // 정리는 삭제 정책을 타지 않는다. WorkflowUsageView 가 실제 조회로 바뀌면
    // workflowService.delete 로 치우던 방식은 정리부터 막힌다.
    @AfterEach
    void tearDown() {
        graphEdgeWriter.removeAll(graphIdOf(workflowId));
        graphNodeWriter.removeAll(graphIdOf(workflowId));
        workflowWriter.remove(workflowId);
    }

    @Test
    @DisplayName("남아 있는 노드 키는 행을 그대로 두고 값만 고친다")
    void keepsRowAndUpdatesValueWhenNodeKeyRemains() {
        Long keptRowId = rowIdOf(nodeRows(), "n1");

        // n1 은 원본만 바꾸고, n2 는 빼고, n3 을 새로 넣는다.
        workflowService.replace(
                workflowId,
                OWNER,
                "그래프 병합",
                Revision.of(1L),
                List.of(placement("n1", replacedSource), placement("n3", addedSource)),
                List.of(GraphFixtures.edge("e2", "n1", "n3")));

        List<GraphNodeEntity> after = nodeRows();

        assertThat(after).extracting(GraphNodeEntity::getNodeKey)
                .containsExactlyInAnyOrder("n1", "n3");
        assertThat(rowIdOf(after, "n1"))
                .as("n1 은 지웠다 다시 넣은 것이 아니라 같은 행이어야 한다")
                .isEqualTo(keptRowId);
        assertThat(referenceOf(after, "n1"))
                .as("같은 행 위에서 원본만 바뀐다")
                .isEqualTo(replacedSource);
    }

    @Test
    @DisplayName("연결선도 같은 키면 행을 그대로 두고 양 끝만 고친다")
    void keepsRowAndUpdatesEndsWhenEdgeKeyRemains() {
        Long keptRowId = edgeRows().getFirst().getId();

        workflowService.replace(
                workflowId,
                OWNER,
                "그래프 병합",
                Revision.of(1L),
                List.of(placement("n2", secondSource), placement("n1", firstSource)),
                List.of(GraphFixtures.edge("e1", "n2", "n1")));

        List<GraphEdgeEntity> after = edgeRows();

        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getId()).isEqualTo(keptRowId);
        assertThat(after.getFirst().getSourceNodeKey()).isEqualTo("n2");
        assertThat(after.getFirst().getTargetNodeKey()).isEqualTo("n1");
    }

    private List<GraphNodeEntity> nodeRows() {
        return graphNodeRepository.findByGraphIdOrderByIdAsc(graphIdOf(workflowId).value());
    }

    private List<GraphEdgeEntity> edgeRows() {
        return graphEdgeRepository.findByGraphIdOrderByIdAsc(graphIdOf(workflowId).value());
    }

    private static Long rowIdOf(List<GraphNodeEntity> nodes, String nodeKey) {
        return find(nodes, nodeKey).getId();
    }

    private static UUID referenceOf(List<GraphNodeEntity> nodes, String nodeKey) {
        return find(nodes, nodeKey).getReferenceId();
    }

    private static GraphNodeEntity find(List<GraphNodeEntity> nodes, String nodeKey) {
        return nodes.stream()
                .filter(node -> node.getNodeKey().equals(nodeKey))
                .findFirst()
                .orElseThrow();
    }

    private static NodePlacement placement(String nodeKey, UUID sourceId) {
        return new NodePlacement(NodeKey.of(nodeKey), SourceType.NODE, sourceId, InputSource.defaultSource());
    }

    private GraphId graphIdOf(WorkflowId id) {
        return GraphId.of(workflowRepository.findById(id.value()).orElseThrow().getGraphId());
    }
}
