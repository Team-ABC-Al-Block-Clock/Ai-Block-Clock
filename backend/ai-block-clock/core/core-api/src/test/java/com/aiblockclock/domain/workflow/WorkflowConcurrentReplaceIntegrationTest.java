package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
class WorkflowConcurrentReplaceIntegrationTest {
    private static final UserId OWNER = UserId.of(9102L);
    private static final Revision READ_BY_BOTH = Revision.of(1L);

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

    private WorkflowId workflowId;
    private UUID seededNode;

    @BeforeEach
    void setUp() {
        seededNode = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        workflowId = workflowService.save(OWNER, "동시 수정", null, placements(), edges()).getId();
    }

    // 정리는 삭제 정책을 타지 않는다. WorkflowUsageView 가 실제 조회로 바뀌면
    // workflowService.delete 로 치우던 방식은 정리부터 막힌다.
    @AfterEach
    void tearDown() {
        graphWriter.removeAll(graphIdOf(workflowId));
        workflowWriter.remove(workflowId);
    }

    @Test
    @DisplayName("두 요청이 같은 회차를 들고 동시에 수정하면 하나만 성공한다")
    void onlyOneReplaceWinsWhenBothCarryTheSameRevision() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        List<Future<Optional<ErrorType>>> attempts = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String name = "동시 수정 " + i;
            attempts.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return replace(name);
            }));
        }

        // 두 스레드가 모두 대기선에 선 뒤에 함께 출발시킨다.
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<Optional<ErrorType>> results = new ArrayList<>();
        for (Future<Optional<ErrorType>> attempt : attempts) {
            results.add(attempt.get(10, TimeUnit.SECONDS));
        }

        pool.shutdown();

        assertThat(results)
                .as("하나는 성공하고 하나는 회차 충돌이어야 한다")
                .containsExactlyInAnyOrder(
                        Optional.empty(), Optional.of(ErrorType.WORKFLOW_REVISION_CONFLICT));

        assertThat(workflowRepository.findById(workflowId.value()).orElseThrow().getRevision())
                .as("성공한 쪽 한 번만 반영되어 회차는 2 다")
                .isEqualTo(2L);
    }

    private Optional<ErrorType> replace(String name) {
        try {
            workflowService.replace(workflowId, OWNER, name, READ_BY_BOTH, placements(), edges());
            return Optional.empty();
        } catch (CoreException e) {
            return Optional.of(e.getErrorType());
        }
    }

    private List<NodePlacement> placements() {
        return List.of(
                new NodePlacement(NodeKey.of("n1"), SourceType.NODE, seededNode, InputSource.defaultSource()),
                new NodePlacement(NodeKey.of("n2"), SourceType.NODE, seededNode, InputSource.defaultSource()));
    }

    private static List<GraphEdge> edges() {
        return List.of(GraphFixtures.edge("e1", "n1", "n2"));
    }

    private GraphId graphIdOf(WorkflowId id) {
        return GraphId.of(workflowRepository.findById(id.value()).orElseThrow().getGraphId());
    }
}
