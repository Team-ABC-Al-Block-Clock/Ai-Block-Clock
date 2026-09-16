package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.workflow.WorkflowEdgeEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeEntity;
import com.aiblockclock.storage.workflow.WorkflowEdgeRepository;
import com.aiblockclock.storage.workflow.WorkflowNodeRepository;

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
    private static final OwnerId OWNER = OwnerId.of(9103L);
    private static final UUID FIRST_SOURCE = UUID.randomUUID();
    private static final UUID SECOND_SOURCE = UUID.randomUUID();
    private static final UUID REPLACED_SOURCE = UUID.randomUUID();
    private static final UUID ADDED_SOURCE = UUID.randomUUID();

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowNodeRepository workflowNodeRepository;

    @Autowired
    private WorkflowEdgeRepository workflowEdgeRepository;

    @Autowired
    private WorkflowWriter workflowWriter;

    private WorkflowId workflowId;

    @BeforeEach
    void setUp() {
        workflowId = workflowService.save(
                OWNER,
                "그래프 병합",
                null,
                List.of(placement("n1", FIRST_SOURCE), placement("n2", SECOND_SOURCE)),
                List.of(GraphFixtures.edge("e1", "n1", "n2"))).getId();
    }

    @AfterEach
    void tearDown() {
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
                List.of(placement("n1", REPLACED_SOURCE), placement("n3", ADDED_SOURCE)),
                List.of(GraphFixtures.edge("e2", "n1", "n3")));

        List<WorkflowNodeEntity> after = nodeRows();

        assertThat(after).extracting(WorkflowNodeEntity::getNodeKey)
                .containsExactlyInAnyOrder("n1", "n3");
        assertThat(rowIdOf(after, "n1"))
                .as("n1 은 지웠다 다시 넣은 것이 아니라 같은 행이어야 한다")
                .isEqualTo(keptRowId);
        assertThat(referenceOf(after, "n1"))
                .as("같은 행 위에서 원본만 바뀐다")
                .isEqualTo(REPLACED_SOURCE);
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
                List.of(placement("n2", SECOND_SOURCE), placement("n1", FIRST_SOURCE)),
                List.of(GraphFixtures.edge("e1", "n2", "n1")));

        List<WorkflowEdgeEntity> after = edgeRows();

        assertThat(after).hasSize(1);
        assertThat(after.getFirst().getId()).isEqualTo(keptRowId);
        assertThat(after.getFirst().getSourceNodeKey()).isEqualTo("n2");
        assertThat(after.getFirst().getTargetNodeKey()).isEqualTo("n1");
    }

    private List<WorkflowNodeEntity> nodeRows() {
        return workflowNodeRepository.findByWorkflowIdOrderByIdAsc(workflowId.value());
    }

    private List<WorkflowEdgeEntity> edgeRows() {
        return workflowEdgeRepository.findByWorkflowIdOrderByIdAsc(workflowId.value());
    }

    private static Long rowIdOf(List<WorkflowNodeEntity> nodes, String nodeKey) {
        return find(nodes, nodeKey).getId();
    }

    private static UUID referenceOf(List<WorkflowNodeEntity> nodes, String nodeKey) {
        return find(nodes, nodeKey).getReferenceId();
    }

    private static WorkflowNodeEntity find(List<WorkflowNodeEntity> nodes, String nodeKey) {
        return nodes.stream()
                .filter(node -> node.getNodeKey().equals(nodeKey))
                .findFirst()
                .orElseThrow();
    }

    private static NodePlacement placement(String nodeKey, UUID sourceId) {
        return new NodePlacement(NodeKey.of(nodeKey), SourceType.NODE, sourceId);
    }
}
