package com.aiblockclock.domain.node;

import com.aiblockclock.domain.agent.AgentProfile;
import com.aiblockclock.domain.agent.AgentService;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodePlacement;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowService;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.graph.GraphNodeRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@NodeMySqlTest
class NodeDefinitionDeletionMySqlTest {
    private static final UserId OWNER = UserId.of(9311L);

    @Autowired
    private NodeDefinitionService nodeDefinitionService;

    @Autowired
    private WorkflowService workflowService;

    @Autowired
    private AgentService agentService;

    @Autowired
    private NodeDefinitionRepository nodeRepository;

    @Autowired
    private GraphNodeRepository graphNodeRepository;

    // 한 번만 돌리면 첫 회차의 커넥션 풀 초기화 · JIT 때문에 두 스레드가 겹치지 않아
    // 락을 지워도 통과한다. 반복해야 락이 실제로 막고 있는지가 드러난다.
    @RepeatedTest(30)
    @DisplayName("삭제와 배치가 동시에 들어와도 지워진 노드를 가리키는 배치는 남지 않는다")
    void deletionAndPlacementNeverLeaveADanglingReference() throws Exception {
        UUID nodeId = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<ErrorType>> deletion = pool.submit(() -> attempt(startTogether, () ->
                    nodeDefinitionService.delete(NodeDefinitionId.of(nodeId), OWNER)));
            Future<Optional<ErrorType>> placement = pool.submit(() -> attempt(startTogether, () ->
                    workflowService.save(OWNER, "동시 배치", null, placements(nodeId), edges())));

            List<Optional<ErrorType>> results =
                    List.of(deletion.get(30, TimeUnit.SECONDS), placement.get(30, TimeUnit.SECONDS));

            assertThat(results.stream().filter(Optional::isEmpty).count())
                    .as("삭제와 배치 중 정확히 하나만 성공해야 한다")
                    .isEqualTo(1);
            assertThat(results)
                    .as("실패한 쪽은 사유를 알 수 있어야 한다")
                    .containsAnyOf(
                            Optional.of(ErrorType.NODE_PLACED_IN_GRAPH),
                            Optional.of(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(graphNodeRepository.existsByReferenceId(nodeId) && nodeRepository.findById(nodeId).isEmpty())
                .as("배치가 남았다면 노드도 남아 있어야 한다")
                .isFalse();
    }

    @RepeatedTest(30)
    @DisplayName("삭제와 등록이 동시에 들어와도 지워진 노드를 가리키는 버전은 남지 않는다")
    void deletionAndRegistrationNeverLeaveADanglingReference() throws Exception {
        UUID nodeId = NodeDefinitionSeeder.seed(nodeRepository, OWNER.value());
        Workflow source = workflowService.save(OWNER, "동시 등록", null, placements(nodeId), edges());
        workflowService.delete(source.getId(), OWNER);

        CyclicBarrier startTogether = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<ErrorType>> deletion = pool.submit(() -> attempt(startTogether, () ->
                    nodeDefinitionService.delete(NodeDefinitionId.of(nodeId), OWNER)));
            Future<Optional<ErrorType>> registration = pool.submit(() -> attempt(startTogether, () ->
                    agentService.register(OWNER, source, Revision.initial(), profile())));

            List<Optional<ErrorType>> results =
                    List.of(deletion.get(30, TimeUnit.SECONDS), registration.get(30, TimeUnit.SECONDS));

            assertThat(results.stream().filter(Optional::isEmpty).count())
                    .as("삭제와 등록 중 정확히 하나만 성공해야 한다")
                    .isEqualTo(1);
            assertThat(results)
                    .as("실패한 쪽은 사유를 알 수 있어야 한다")
                    .containsAnyOf(
                            Optional.of(ErrorType.NODE_PLACED_IN_GRAPH),
                            Optional.of(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(graphNodeRepository.existsByReferenceId(nodeId) && nodeRepository.findById(nodeId).isEmpty())
                .as("버전이 남았다면 노드도 남아 있어야 한다")
                .isFalse();
    }

    private static AgentProfile profile() {
        return new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다", null, null, null);
    }

    private static Optional<ErrorType> attempt(CyclicBarrier startTogether, Runnable operation) throws Exception {
        startTogether.await(10, TimeUnit.SECONDS);

        try {
            operation.run();
            return Optional.empty();
        } catch (CoreException exception) {
            return Optional.of(exception.getErrorType());
        }
    }

    private static List<NodePlacement> placements(UUID nodeId) {
        return List.of(
                GraphFixtures.placement("n1", nodeId), GraphFixtures.placement("n2", nodeId));
    }

    private static List<GraphEdge> edges() {
        return List.of(GraphFixtures.edge("e1", "n1", "n2"));
    }
}
