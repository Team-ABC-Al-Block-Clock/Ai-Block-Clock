package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

class AgentNodeTest {
    @Test
    @DisplayName("에이전트 노드는 버전까지 고정해야 만들어진다")
    void requiresVersion() {
        assertThatThrownBy(() -> new AgentNode(NodeKey.of("n1"), UUID.randomUUID(), null, InputSource.defaultSource()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_REFERENCE_MISMATCH);
    }

    @Test
    @DisplayName("저장과 응답이 쓰는 평면 참조로 자신을 내놓는다")
    void flattensToReference() {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        AgentNode node = new AgentNode(NodeKey.of("n1"), agentId, versionId, InputSource.defaultSource());

        assertThat(node.sourceType()).isEqualTo(SourceType.AGENT);
        assertThat(node.reference()).isEqualTo(NodeReference.of(agentId, versionId));
    }
}
