package com.aiblockclock.domain.workflow;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static com.aiblockclock.domain.workflow.GraphFixtures.edge;
import static com.aiblockclock.domain.workflow.GraphFixtures.node;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

class GraphLimitsTest {
    @Test
    @DisplayName("한도 안의 그래프는 통과시킨다")
    void allowsGraphWithinLimit() {
        assertThatCode(() -> GraphLimits.defaults().ensureWithin(GraphFixtures.straightLine()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("노드가 없으면 저장할 수 없다")
    void rejectsEmptyGraph() {
        assertThatThrownBy(() -> GraphLimits.defaults().ensureWithin(WorkflowGraph.empty()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_GRAPH_EMPTY);
    }

    @Test
    @DisplayName("순환이 있으면 막는다")
    void rejectsCycle() {
        WorkflowGraph graph = WorkflowGraph.of(
                List.of(node("n1"), node("n2")),
                List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n1")));

        assertThatThrownBy(() -> GraphLimits.defaults().ensureWithin(graph))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_GRAPH_CYCLE);
    }

    @Test
    @DisplayName("노드 수 제한을 넘으면 막는다")
    void rejectsTooManyNodes() {
        assertThatThrownBy(() -> new GraphLimits(2, 10).ensureWithin(GraphFixtures.straightLine()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("연결 깊이 제한을 넘으면 막는다")
    void rejectsTooDeepGraph() {
        assertThatThrownBy(() -> new GraphLimits(20, 2).ensureWithin(GraphFixtures.chainOf(4)))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_DEPTH_LIMIT_EXCEEDED);
    }
}
