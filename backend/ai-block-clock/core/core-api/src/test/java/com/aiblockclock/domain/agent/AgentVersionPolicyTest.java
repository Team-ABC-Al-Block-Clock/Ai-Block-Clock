package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.Workflow;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.domain.workflow.WorkflowId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

class AgentVersionPolicyTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    private static AgentVersionPolicy policy(Set<AgentId> ownedAgents) {
        return new AgentVersionPolicy(
                (agentId, owner) -> OWNER.equals(owner) && ownedAgents.contains(agentId),
                (nodeId, owner) -> OWNER.equals(owner));
    }

    private static AgentVersionPolicy policyWithForeignNodes() {
        return new AgentVersionPolicy(
                (agentId, owner) -> true,
                (nodeId, owner) -> false);
    }

    private static Workflow emptyWorkflow() {
        return Workflow.create(WorkflowId.newId(), OWNER, "성적 분석", null, WorkflowGraph.empty());
    }

    @Test
    @DisplayName("검증을 통과한 내 워크플로우로는 에이전트 버전을 만들 수 있다")
    void allowsValidatedOwnWorkflow() {
        Workflow workflow = emptyWorkflow();
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());
        workflow.markValidated();

        assertThatCode(() -> policy(Set.of()).ensureVersionable(workflow, OWNER))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("소유자가 아니면 에이전트 버전을 만들 수 없다")
    void rejectsNonOwner() {
        Workflow workflow = emptyWorkflow();
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());
        workflow.markValidated();

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OTHER))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("검증 전 워크플로우로는 에이전트 버전을 만들 수 없다")
    void rejectsNotValidated() {
        Workflow workflow = emptyWorkflow();
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OWNER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_WORKFLOW_NOT_VALIDATED);
    }

    @Test
    @DisplayName("남의 기본 노드가 섞여 있으면 에이전트 버전을 만들 수 없다")
    void rejectsForeignBaseNode() {
        Workflow workflow = emptyWorkflow();
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());
        workflow.markValidated();

        assertThatThrownBy(() -> policyWithForeignNodes().ensureVersionable(workflow, OWNER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }

    @Test
    @DisplayName("버전 생성 시점에 소유권을 다시 훑어 남의 것을 잡아낸다")
    void rechecksOwnershipAtVersionCreation() {
        Workflow workflow = emptyWorkflow();

        workflow.replaceGraph(OWNER, AgentGraphFixtures.withAgent(AgentId.newId(), AgentVersionId.newId()),
                Revision.initial());
        workflow.markValidated();

        assertThatThrownBy(() -> policy(Set.of()).ensureVersionable(workflow, OWNER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.AGENT_FOREIGN_NODE_INCLUDED);
    }
}
