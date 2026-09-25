package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.InputFieldType;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
@SpringBootTest
class WorkflowIoNodeIntegrationTest {
    private static final UserId OWNER = UserId.of(9107L);

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private WorkflowReader workflowReader;

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
        nodeRepository.deleteById(seededNode);
    }

    @Test
    @DisplayName("놓아 둔 입력 항목이 다시 불러도 그대로다")
    void restoresInputFields() {
        WorkflowId id = save(List.of(
                GraphFixtures.startPlacement("s1", List.of(
                        new InputField("brand_name", "브랜드명", InputFieldType.TEXT, true, List.of()),
                        new InputField("price_tier", "가격대", InputFieldType.CHOICE, false,
                                List.of("보급형", "중가", "프리미엄")))),
                GraphFixtures.placement("n1", seededNode),
                GraphFixtures.endPlacement("e1", ResultShape.TABLE, ResultFormat.XLSX)));

        IoNode start = (IoNode) nodeOf(id, "s1");

        assertThat(start.ioType()).isEqualTo(IoType.START);
        assertThat(start.fields())
                .extracting(InputField::key, InputField::label, InputField::type, InputField::required)
                .containsExactly(tuple("brand_name", "브랜드명", InputFieldType.TEXT, true),
                        tuple("price_tier", "가격대", InputFieldType.CHOICE, false));
        assertThat(start.fields().get(1).choices()).containsExactly("보급형", "중가", "프리미엄");
    }

    @Test
    @DisplayName("끝 노드가 고른 구조와 형식이 다시 불러도 그대로다")
    void restoresResultShapeAndFormat() {
        WorkflowId id = save(List.of(
                GraphFixtures.startPlacement("s1", List.of()),
                GraphFixtures.placement("n1", seededNode),
                GraphFixtures.endPlacement("e1", ResultShape.TABLE, ResultFormat.XLSX)));

        IoNode end = (IoNode) nodeOf(id, "e1");

        assertThat(end.ioType()).isEqualTo(IoType.END);
        assertThat(end.resultShape()).isEqualTo(ResultShape.TABLE);
        assertThat(end.resultFormat()).isEqualTo(ResultFormat.XLSX);
        assertThat(end.fields()).isEmpty();
    }

    @Test
    @DisplayName("입력 항목을 비워 두면 비운 채로 돌아온다")
    void restoresEmptyInputFields() {
        WorkflowId id = save(List.of(
                GraphFixtures.startPlacement("s1", List.of()),
                GraphFixtures.placement("n1", seededNode),
                GraphFixtures.endPlacement("e1", ResultShape.DOCUMENT, ResultFormat.PDF)));

        assertThat(((IoNode) nodeOf(id, "s1")).fields()).isEmpty();
    }

    @Test
    @DisplayName("고쳐 저장하면 바뀐 입력 항목으로 덮인다")
    void replacesInputFields() {
        WorkflowId id = save(List.of(
                GraphFixtures.startPlacement("s1", List.of(GraphFixtures.field("brand_name"))),
                GraphFixtures.placement("n1", seededNode),
                GraphFixtures.endPlacement("e1", ResultShape.DOCUMENT, ResultFormat.PDF)));

        workflowService.replace(id, OWNER, "다시 놓는다", workflowReader.read(id).getRevision(),
                List.of(GraphFixtures.startPlacement("s1", List.of(GraphFixtures.field("target"))),
                        GraphFixtures.placement("n1", seededNode),
                        GraphFixtures.endPlacement("e1", ResultShape.TEXT, ResultFormat.MD)),
                edges());

        assertThat(((IoNode) nodeOf(id, "s1")).fields()).extracting(InputField::key).containsExactly("target");
        assertThat(((IoNode) nodeOf(id, "e1")).resultFormat()).isEqualTo(ResultFormat.MD);
    }

    @Test
    @DisplayName("시작 노드를 둘 놓으면 저장되지 않는다")
    void rejectsTwoStartNodes() {
        assertThatThrownBy(() -> workflowService.save(OWNER, "시작이 둘", null,
                List.of(GraphFixtures.startPlacement("s1", List.of()),
                        GraphFixtures.startPlacement("s2", List.of()),
                        GraphFixtures.placement("n1", seededNode)),
                List.of(GraphFixtures.edge("g1", "s1", "n1"), GraphFixtures.edge("g2", "s2", "n1"))))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_IO_NODE_DUPLICATED);
    }

    private WorkflowId save(List<NodePlacement> nodes) {
        Workflow workflow = workflowService.save(OWNER, "시작과 끝을 놓는다", null, nodes, edges());
        saved.add(workflow.getId());

        return workflow.getId();
    }

    private static List<GraphEdge> edges() {
        return List.of(GraphFixtures.edge("g1", "s1", "n1"), GraphFixtures.edge("g2", "n1", "e1"));
    }

    private GraphNode nodeOf(WorkflowId id, String key) {
        return workflowReader.read(id).getGraph().findNode(NodeKey.of(key)).orElseThrow();
    }
}
