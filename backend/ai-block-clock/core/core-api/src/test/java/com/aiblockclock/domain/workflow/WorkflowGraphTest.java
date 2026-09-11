package com.aiblockclock.domain.workflow;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static com.aiblockclock.domain.workflow.GraphFixtures.agent;
import static com.aiblockclock.domain.workflow.GraphFixtures.edge;
import static com.aiblockclock.domain.workflow.GraphFixtures.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class WorkflowGraphTest {
    @Nested
    @DisplayName("다시 읽을 수 있는 문서인지 본다")
    class Construction {
        @Test
        @DisplayName("노드 키가 겹치면 만들어지지 않는다")
        void rejectsDuplicateNodeKey() {
            assertThatThrownBy(() -> WorkflowGraph.of(List.of(node("n1"), node("n1")), List.of()))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_DUPLICATE_NODE_KEY);
        }

        @Test
        @DisplayName("연결선 키가 겹치면 만들어지지 않는다")
        void rejectsDuplicateEdgeKey() {
            assertThatThrownBy(() -> WorkflowGraph.of(
                    List.of(node("n1"), node("n2"), node("n3")),
                    List.of(edge("e1", "n1", "n2"), edge("e1", "n2", "n3"))))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_DUPLICATE_EDGE_KEY);
        }

        @Test
        @DisplayName("없는 노드를 가리키는 연결선은 거부한다")
        void rejectsDanglingEdge() {
            assertThatThrownBy(() -> WorkflowGraph.of(
                    List.of(node("n1")),
                    List.of(edge("e1", "n1", "없는노드"))))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_DANGLING_EDGE);
        }

        @Test
        @DisplayName("순환은 그래프로는 만들어진다. 막는 것은 GraphLimits 다")
        void buildsCycleAsIs() {
            assertThatCode(() -> WorkflowGraph.of(
                    List.of(node("n1"), node("n2")),
                    List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n1"))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("담은 대로 보존한다")
    class Preservation {
        @Test
        @DisplayName("담은 순서를 바꾸지 않는다")
        void keepsInputOrder() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n3"), node("n1"), node("n2")),
                    List.of(edge("e2", "n2", "n3"), edge("e1", "n1", "n2")));

            assertThat(graph.getNodes()).extracting(n -> n.key().value()).containsExactly("n3", "n1", "n2");
            assertThat(graph.getEdges()).extracting(e -> e.key().value()).containsExactly("e2", "e1");
        }

        @Test
        @DisplayName("밖에서 넘긴 목록을 고쳐도 그래프는 그대로다")
        void detachesFromCallerList() {
            List<GraphNode> nodes = new ArrayList<>(List.of(node("n1"), node("n2")));
            WorkflowGraph graph = WorkflowGraph.of(nodes, List.of(edge("e1", "n1", "n2")));

            nodes.clear();

            assertThat(graph.nodeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("빈 그래프는 하나의 값이다")
        void emptyGraph() {
            assertThat(WorkflowGraph.of(List.of(), List.of())).isSameAs(WorkflowGraph.empty());
            assertThat(WorkflowGraph.empty().isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("그래프를 읽는다")
    class Queries {
        @Test
        @DisplayName("노드 수와 연결선 수를 센다")
        void counts() {
            WorkflowGraph graph = GraphFixtures.straightLine();

            assertThat(graph.nodeCount()).isEqualTo(3);
            assertThat(graph.edgeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("키로 노드를 찾는다")
        void findsNodeByKey() {
            WorkflowGraph graph = GraphFixtures.straightLine();

            assertThat(graph.findNode(NodeKey.of("n2"))).isPresent();
            assertThat(graph.findNode(NodeKey.of("없는노드"))).isEmpty();
        }

        @Test
        @DisplayName("에이전트를 배치했는지는 노드가 스스로 답한다")
        void tellsAgentNodeApart() {
            WorkflowGraph graph = GraphFixtures.withAgent(UUID.randomUUID(), UUID.randomUUID());

            assertThat(graph.getNodes()).filteredOn(AgentNode.class::isInstance)
                    .extracting(n -> n.key().value())
                    .containsExactly("n2");
        }

        @Test
        @DisplayName("한 줄로 이어지면 그 길이를 센다")
        void measuresChainLength() {
            assertThat(GraphFixtures.straightLine().chainLength()).isEqualTo(3);
        }

        @Test
        @DisplayName("노드 하나뿐이어도 한 줄이다")
        void countsSingleNode() {
            assertThat(WorkflowGraph.of(List.of(node("n1")), List.of()).chainLength()).isEqualTo(1);
        }

        @Test
        @DisplayName("갈라지면 한 줄이 아니다")
        void rejectsFork() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n1"), node("n2"), node("n3")),
                    List.of(edge("e1", "n1", "n2"), edge("e2", "n1", "n3")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR);
        }

        @Test
        @DisplayName("합쳐져도 한 줄이 아니다")
        void rejectsMerge() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n1"), node("n2"), node("n3")),
                    List.of(edge("e1", "n1", "n3"), edge("e2", "n2", "n3")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR);
        }

        @Test
        @DisplayName("끊겨 있으면 한 줄이 아니다")
        void rejectsDisconnectedNode() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n1"), node("n2"), node("n3")),
                    List.of(edge("e1", "n1", "n2")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR);
        }

        @Test
        @DisplayName("자기 자신으로 도는 연결선은 순환이다")
        void detectsSelfLoopAsCycle() {
            WorkflowGraph graph = WorkflowGraph.of(List.of(node("n1")), List.of(edge("e1", "n1", "n1")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        @Test
        @DisplayName("시작점이 없으면 순환이다")
        void detectsCycleWithoutStart() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n1"), node("n2")),
                    List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n1")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        @Test
        @DisplayName("시작점에서 닿지 않는 곳이 돌고 있으면 순환이다")
        void detectsDetachedCycle() {
            WorkflowGraph graph = WorkflowGraph.of(
                    List.of(node("n1"), node("n2"), node("n3")),
                    List.of(edge("e1", "n2", "n3"), edge("e2", "n3", "n2")));

            assertThatThrownBy(graph::chainLength)
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }
    }
}
