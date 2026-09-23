package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowGraphDescriberTest {
    private static final UserId REQUESTER = UserId.of(1L);

    @Test
    @DisplayName("종류마다 한 번씩만 물어본다 — 자리 수만큼 묻지 않는다")
    void asksEachDescriberOnce() {
        UUID agentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        WorkflowGraph graph = GraphFixtures.withAgent(agentId, versionId);
        CountingDescriber nodes = new CountingDescriber(SourceType.NODE, Map.of());
        CountingDescriber agents = new CountingDescriber(SourceType.AGENT, Map.of());

        new WorkflowGraphDescriber(List.of(nodes, agents)).describe(REQUESTER, graph);

        assertThat(nodes.calls).isEqualTo(1);
        assertThat(nodes.received).hasSize(2);
        assertThat(agents.calls).isEqualTo(1);
        assertThat(agents.received).hasSize(1);
    }

    @Test
    @DisplayName("푼 이름과 소유 판정을 노드 키에 붙여 돌려준다")
    void mapsDescribedSourcesOntoNodeKeys() {
        UUID agentId = UUID.randomUUID();
        WorkflowGraph graph = GraphFixtures.withAgent(agentId, UUID.randomUUID());
        UUID firstNodeId = graph.findNode(NodeKey.of("n1")).orElseThrow().reference().referenceId();

        Map<NodeKey, PlacedSource> described = new WorkflowGraphDescriber(List.of(
                new CountingDescriber(SourceType.NODE, Map.of(firstNodeId, new PlacedSource("성적 분석", true))),
                new CountingDescriber(SourceType.AGENT, Map.of(agentId, new PlacedSource("남의 에이전트", false)))))
                .describe(REQUESTER, graph);

        assertThat(described.get(NodeKey.of("n1"))).isEqualTo(new PlacedSource("성적 분석", true));
        assertThat(described.get(NodeKey.of("n2"))).isEqualTo(new PlacedSource("남의 에이전트", false));
    }

    @Test
    @DisplayName("원본을 못 찾은 자리는 이름이 null 이고 내 것이 아니다")
    void fallsBackToUnknownWhenSourceIsGone() {
        WorkflowGraph graph = GraphFixtures.chainOf(2);

        Map<NodeKey, PlacedSource> described =
                new WorkflowGraphDescriber(List.of(new CountingDescriber(SourceType.NODE, Map.of())))
                        .describe(REQUESTER, graph);

        assertThat(described.get(NodeKey.of("n1"))).isEqualTo(PlacedSource.unknown());
        assertThat(described.get(NodeKey.of("n1")).name()).isNull();
        assertThat(described.get(NodeKey.of("n1")).mine()).isFalse();
    }

    @Test
    @DisplayName("빈 그래프는 아무것도 묻지 않는다")
    void describesNothingForEmptyGraph() {
        CountingDescriber nodes = new CountingDescriber(SourceType.NODE, Map.of());

        assertThat(new WorkflowGraphDescriber(List.of(nodes)).describe(REQUESTER, WorkflowGraph.empty())).isEmpty();
        assertThat(nodes.calls).isZero();
    }

    private static final class CountingDescriber implements NodeSourceDescriber {
        private final SourceType sourceType;
        private final Map<UUID, PlacedSource> answers;
        private int calls;
        private List<NodeReference> received = List.of();

        private CountingDescriber(SourceType sourceType, Map<UUID, PlacedSource> answers) {
            this.sourceType = sourceType;
            this.answers = answers;
        }

        @Override
        public SourceType sourceType() {
            return sourceType;
        }

        @Override
        public Map<UUID, PlacedSource> describe(UserId requester, List<NodeReference> references) {
            calls++;
            received = references;

            return answers;
        }
    }
}
