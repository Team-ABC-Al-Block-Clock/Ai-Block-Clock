package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.storage.graph.GraphNodeEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

class GraphConverterTest {
    private static final UUID GRAPH_ID = UUID.randomUUID();

    @Test
    @DisplayName("판별자가 NODE 인 행은 기본 노드로 복원된다")
    void restoresBaseNode() {
        UUID sourceId = UUID.randomUUID();

        WorkflowGraph graph = toDomain(new GraphNodeEntity(GRAPH_ID, "n1", SourceType.NODE, sourceId, null, InputSource.defaultSource()));

        assertThat(graph.getNodes()).containsExactly(new BaseNode(NodeKey.of("n1"), sourceId, InputSource.defaultSource()));
    }

    @Test
    @DisplayName("판별자가 AGENT 인 행은 에이전트 노드로 복원된다")
    void restoresAgentNode() {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        WorkflowGraph graph = toDomain(new GraphNodeEntity(GRAPH_ID, "n1", SourceType.AGENT, agentId, versionId, InputSource.defaultSource()));

        assertThat(graph.getNodes()).containsExactly(new AgentNode(NodeKey.of("n1"), agentId, versionId, InputSource.defaultSource()));
    }

    @Test
    @DisplayName("기본 노드 행에 버전이 차 있으면 복원하지 않는다")
    void rejectsVersionedBaseNodeRow() {
        GraphNodeEntity corrupt =
                new GraphNodeEntity(GRAPH_ID, "n1", SourceType.NODE, UUID.randomUUID(),
                        UUID.randomUUID(), InputSource.defaultSource());

        assertThatThrownBy(() -> toDomain(corrupt))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH);
    }

    @Test
    @DisplayName("에이전트 행에 버전이 비어 있으면 복원하지 않는다")
    void rejectsUnversionedAgentRow() {
        GraphNodeEntity corrupt =
                new GraphNodeEntity(GRAPH_ID, "n1", SourceType.AGENT, UUID.randomUUID(), null, InputSource.defaultSource());

        assertThatThrownBy(() -> toDomain(corrupt))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH);
    }

    private static WorkflowGraph toDomain(GraphNodeEntity node) {
        return GraphConverter.toGraph(List.of(node), List.of());
    }
}
