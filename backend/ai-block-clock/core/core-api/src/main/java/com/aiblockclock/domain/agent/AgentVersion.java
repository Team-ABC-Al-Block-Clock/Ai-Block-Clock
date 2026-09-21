package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentVersion {
    private final AgentVersionId id;
    private final AgentId agentId;
    private final VersionNumber number;
    private final SourceWorkflow source;

    private final WorkflowGraph snapshot;

    private final VersionCost cost;

    private Tier tier = Tier.initial();
    private PublicationState state = PublicationState.DRAFT;

    public static AgentVersion first(
            AgentVersionId id,
            AgentId agentId,
            SourceWorkflow source,
            WorkflowGraph snapshot,
            VersionCost cost
    ) {
        requireExecutable(snapshot);
        return new AgentVersion(id, agentId, VersionNumber.first(), source, snapshot, cost);
    }

    public static AgentVersion next(
            AgentVersion previous,
            AgentVersionId id,
            SourceWorkflow source,
            WorkflowGraph snapshot,
            VersionCost cost
    ) {
        requireExecutable(snapshot);
        return new AgentVersion(id, previous.agentId, previous.number.next(), source, snapshot, cost);
    }

    private static void requireExecutable(WorkflowGraph snapshot) {
        if (snapshot.isEmpty()) {
            throw new CoreException(ErrorType.AGENT_WORKFLOW_EMPTY);
        }
    }

    public void changeTier(Tier tier) {
        ensureNotPublished();
        this.tier = tier;
    }

    public void publish() {
        ensureNotPublished();
        this.state = PublicationState.PUBLISHED;
    }

    private void ensureNotPublished() {
        if (isPublished()) {
            throw new CoreException(ErrorType.AGENT_VERSION_ALREADY_PUBLISHED);
        }
    }

    public boolean isPublished() {
        return state == PublicationState.PUBLISHED;
    }

    public AbcAmount finalPrice() {
        return cost.baseCost().multiply(BigDecimal.ONE.add(tier.premiumRate()));
    }
}
