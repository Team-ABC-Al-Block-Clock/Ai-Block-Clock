package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.WorkflowGraph;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentPlacementTest {
    @Test
    @DisplayName("그래프의 에이전트 참조를 에이전트와 버전으로 읽는다")
    void readsPlacementsFromGraph() {
        AgentId agentId = AgentId.newId();
        AgentVersionId versionId = AgentVersionId.newId();
        WorkflowGraph graph = AgentGraphFixtures.withAgent(agentId, versionId);

        assertThat(AgentPlacement.in(graph)).containsExactly(new AgentPlacement(agentId, versionId));
    }

    @Test
    @DisplayName("에이전트 노드가 없으면 빈 목록이다")
    void emptyWhenNoAgentNode() {
        assertThat(AgentPlacement.in(GraphFixtures.straightLine())).isEmpty();
    }

    @Test
    @DisplayName("참조 하나에서도 읽어낼 수 있다")
    void readsFromSingleReference() {
        AgentId agentId = AgentId.newId();
        AgentVersionId versionId = AgentVersionId.newId();

        assertThat(AgentPlacement.from(NodeReference.of(agentId.value(), versionId.value())))
                .isEqualTo(new AgentPlacement(agentId, versionId));
    }

    @Test
    @DisplayName("배치를 다시 노드 참조로 되돌릴 수 있다")
    void convertsBackToNodeReference() {
        AgentId agentId = AgentId.newId();
        AgentVersionId versionId = AgentVersionId.newId();
        AgentPlacement placement = new AgentPlacement(agentId, versionId);

        assertThat(placement.toNodeReference().referenceId()).isEqualTo(agentId.value());
        assertThat(placement.toNodeReference().versionId()).isEqualTo(versionId.value());
    }
}
