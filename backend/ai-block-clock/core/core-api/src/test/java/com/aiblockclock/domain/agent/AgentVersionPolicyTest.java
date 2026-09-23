package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.Timestamps;
import com.aiblockclock.domain.node.NodeDefinition;
import com.aiblockclock.domain.node.NodeDefinitionId;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeReference;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowFixtures;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

class AgentVersionPolicyTest {
    private static final UserId OWNER = UserId.of(1L);
    private static final UserId OTHER = UserId.of(2L);

    private static AgentVersionPolicy policy(Set<AgentId> ownedAgents) {
        AgentReader agentReader = mock(AgentReader.class);
        given(agentReader.isOwnedBy(any(), any()))
                .willAnswer(call -> OWNER.equals(call.getArgument(1)) && ownedAgents.contains(call.getArgument(0)));

        return new AgentVersionPolicy(agentReader);
    }

    @Test
    @DisplayName("내 노드로만 이루어진 워크플로우로는 에이전트 버전을 만들 수 있다")
    void allowsWorkflowOfOwnNodes() {
        Workflow workflow = WorkflowFixtures.owned(OWNER, GraphFixtures.straightLine());

        assertThatCode(() -> policy(Set.of())
                .ensureVersionable(workflow, OWNER, nodesOwnedBy(workflow.getGraph(), OWNER)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소유자가 아니면 에이전트 버전을 만들 수 없다")
    void rejectsNonOwner() {
        Workflow workflow = WorkflowFixtures.owned(OWNER, GraphFixtures.straightLine());
        List<NodeDefinition> nodes = nodesOwnedBy(workflow.getGraph(), OWNER);

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OTHER, nodes))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_FORBIDDEN);
    }

    @Test
    @DisplayName("남의 기본 노드가 섞여 있으면 에이전트 버전을 만들 수 없다")
    void rejectsForeignBaseNode() {
        Workflow workflow = WorkflowFixtures.owned(OWNER, GraphFixtures.straightLine());
        List<NodeDefinition> nodes = nodesOwnedBy(workflow.getGraph(), OTHER);

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OWNER, nodes))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    @Test
    @DisplayName("배치된 노드가 사라졌으면 500 이 아니라 찾을 수 없다고 끊는다")
    void rejectsMissingBaseNode() {
        Workflow workflow = WorkflowFixtures.owned(OWNER, GraphFixtures.straightLine());

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OWNER, List.of()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_NODE_SOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("버전 생성 시점에 소유권을 다시 훑어 남의 에이전트를 잡아낸다")
    void rejectsForeignAgentPlacement() {
        Workflow workflow = WorkflowFixtures.owned(OWNER,
                AgentGraphFixtures.withAgent(AgentId.newId(), AgentVersionId.newId()));
        List<NodeDefinition> nodes = nodesOwnedBy(workflow.getGraph(), OWNER);

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OWNER, nodes))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    private static List<NodeDefinition> nodesOwnedBy(WorkflowGraph graph, UserId owner) {
        return graph.baseNodeReferences().stream()
                .map(NodeReference::referenceId)
                .map(id -> NodeDefinition.restore(
                        NodeDefinitionId.of(id),
                        owner,
                        Timestamps.unsaved(),
                        new NodeSpec("시드 노드", NodeType.LLM, AiModel.defaultModel(),
                                AbcAmount.of(new BigDecimal("3"))),
                        Prompt.of("프롬프트 원문")))
                .toList();
    }
}
