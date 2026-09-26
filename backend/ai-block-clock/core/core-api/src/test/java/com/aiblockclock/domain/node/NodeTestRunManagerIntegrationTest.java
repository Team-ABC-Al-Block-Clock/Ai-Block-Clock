package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.NodeTestRunStatus;
import com.aiblockclock.storage.node.NodeDefinitionRepository;
import com.aiblockclock.storage.node.NodeTestRunRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.testsupport.NodeDefinitionSeeder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// 잠금이 실제로 줄을 세우는지 보려면 요청마다 따로 커밋되어야 해서 테스트 트랜잭션을 쓰지 않고, 따로 떼어 낸 DB 에서 돈다.
@Tag("integration")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:node_test_run;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class NodeTestRunManagerIntegrationTest {
    private static final UserId OWNER = UserId.of(9701L);

    @Autowired
    private NodeTestRunManager nodeTestRunManager;

    @Autowired
    private NodeDefinitionReader nodeDefinitionReader;

    @Autowired
    private NodeDefinitionRepository nodeDefinitionRepository;

    @Autowired
    private NodeTestRunRepository nodeTestRunRepository;

    private NodeDefinitionId nodeId;

    @BeforeEach
    void 노드_하나를_둔다() {
        nodeId = NodeDefinitionId.of(NodeDefinitionSeeder.seed(nodeDefinitionRepository, OWNER.value()));
    }

    @AfterEach
    void cleanUp() {
        nodeTestRunRepository.deleteAllInBatch();
        nodeDefinitionRepository.deleteAllInBatch();
    }

    @Test
    void 같은_노드의_테스트가_한꺼번에_들어와도_하루_한도를_넘겨_받지_않는다() throws Exception {
        List<Callable<?>> reservations = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            reservations.add(() -> nodeTestRunManager.reserve(nodeId, OWNER, false));
        }

        List<Outcome> outcomes = runConcurrently(reservations);

        assertThat(outcomes).filteredOn(Outcome.DONE::equals).hasSize(5);
        assertThat(outcomes).filteredOn(Outcome.LIMITED::equals).hasSize(3);
        assertThat(nodeTestRunRepository.count()).isEqualTo(5);
    }

    // 셋의 중앙값 0.004 × 1.5 = 0.006. 셋이 동시에 닫혀도 늦게 닫는 쪽이 먼저 닫힌 기록까지 보고 정한다.
    @Test
    void 같은_노드의_테스트가_동시에_끝나도_모든_기록을_보고_원가를_정한다() throws Exception {
        List<NodeTestRunId> running = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            NodeTestRun reserved = nodeTestRunManager.reserve(nodeId, OWNER, false);
            running.add(nodeTestRunManager.dispatch(reserved.id(), "run-" + i).id());
        }

        List<String> costs = List.of("0.002", "0.004", "0.05");
        List<Callable<?>> finishes = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            NodeTestRunId id = running.get(i);
            BigDecimal cost = new BigDecimal(costs.get(i));
            finishes.add(() -> nodeTestRunManager.finish(id,
                    new NodeTestRunOutcome(NodeTestRunStatus.SUCCEEDED, cost, null, null)));
        }

        assertThat(runConcurrently(finishes)).containsOnly(Outcome.DONE);
        assertThat(nodeDefinitionReader.read(nodeId).getSpec().baseCost().value()).isEqualByComparingTo("0.006");
    }

    @Test
    void 이미_닫힌_테스트는_다시_닫지_않는다() {
        NodeTestRun reserved = nodeTestRunManager.reserve(nodeId, OWNER, false);
        NodeTestRunId id = nodeTestRunManager.dispatch(reserved.id(), "run-1").id();

        assertThat(nodeTestRunManager.finish(id,
                new NodeTestRunOutcome(NodeTestRunStatus.SUCCEEDED, new BigDecimal("0.004"), null, null))).isPresent();
        assertThat(nodeTestRunManager.finish(id,
                new NodeTestRunOutcome(NodeTestRunStatus.FAILED, null, "MODEL_TIMEOUT", null))).isEmpty();
        assertThat(nodeTestRunRepository.findById(id.value()).orElseThrow().getStatus())
                .isEqualTo(NodeTestRunStatus.SUCCEEDED);
    }

    private enum Outcome { DONE, LIMITED }

    private List<Outcome> runConcurrently(List<Callable<?>> tasks) throws Exception {
        CyclicBarrier startTogether = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Future<?>> futures = new ArrayList<>();
        List<Outcome> outcomes = new ArrayList<>();

        try {
            for (Callable<?> task : tasks) {
                futures.add(executor.submit(() -> {
                    startTogether.await(10, TimeUnit.SECONDS);

                    return task.call();
                }));
            }

            for (Future<?> future : futures) {
                outcomes.add(outcomeOf(future));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        return outcomes;
    }

    private static Outcome outcomeOf(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);

            return Outcome.DONE;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof CoreException failure
                    && failure.getErrorType() == ErrorType.NODE_TEST_RUN_LIMIT_EXCEEDED) {
                return Outcome.LIMITED;
            }

            throw exception;
        }
    }
}
