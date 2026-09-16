package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowNodeEntity;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

class WorkflowEntityConverterTest {
    private static final UUID WORKFLOW_ID = UUID.randomUUID();

    @Test
    @DisplayName("판별자가 NODE 인 행은 기본 노드로 복원된다")
    void restoresBaseNode() {
        UUID sourceId = UUID.randomUUID();

        WorkflowGraph graph = toDomain(new WorkflowNodeEntity(WORKFLOW_ID, "n1", SourceType.NODE, sourceId, null));

        assertThat(graph.getNodes()).containsExactly(new BaseNode(NodeKey.of("n1"), sourceId));
    }

    @Test
    @DisplayName("판별자가 AGENT 인 행은 에이전트 노드로 복원된다")
    void restoresAgentNode() {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        WorkflowGraph graph = toDomain(new WorkflowNodeEntity(WORKFLOW_ID, "n1", SourceType.AGENT, agentId, versionId));

        assertThat(graph.getNodes()).containsExactly(new AgentNode(NodeKey.of("n1"), agentId, versionId));
    }

    @Test
    @DisplayName("기본 노드 행에 버전이 차 있으면 복원하지 않는다")
    void rejectsVersionedBaseNodeRow() {
        WorkflowNodeEntity corrupt =
                new WorkflowNodeEntity(WORKFLOW_ID, "n1", SourceType.NODE, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> toDomain(corrupt))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH);
    }

    @Test
    @DisplayName("에이전트 행에 버전이 비어 있으면 복원하지 않는다")
    void rejectsUnversionedAgentRow() {
        WorkflowNodeEntity corrupt =
                new WorkflowNodeEntity(WORKFLOW_ID, "n1", SourceType.AGENT, UUID.randomUUID(), null);

        assertThatThrownBy(() -> toDomain(corrupt))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH);
    }

    private static WorkflowGraph toDomain(WorkflowNodeEntity node) {
        WorkflowEntity entity = new WorkflowEntity(
                WORKFLOW_ID, 1L, "워크플로우", null, ValidationStatus.NOT_VALIDATED, 1L);
        return WorkflowEntityConverter.toDomain(entity, List.of(node), List.of()).getGraph();
    }
}
