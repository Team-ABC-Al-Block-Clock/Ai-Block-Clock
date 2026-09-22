package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.WorkflowGraph;
import com.aiblockclock.enums.agent.PublicationState;
import com.aiblockclock.enums.agent.Tier;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Getter;

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
    private final Tier tier;
    private final PublicationState state;

    public static AgentVersion first(
            AgentVersionId id,
            AgentId agentId,
            SourceWorkflow source,
            WorkflowGraph snapshot,
            VersionCost cost
    ) {
        requireExecutable(snapshot);

        return new AgentVersion(id, agentId, VersionNumber.first(), source, snapshot, cost,
                Tier.initial(), PublicationState.DRAFT);
    }

    public static AgentVersion next(
            AgentVersion previous,
            AgentVersionId id,
            SourceWorkflow source,
            WorkflowGraph snapshot,
            VersionCost cost
    ) {
        requireExecutable(snapshot);

        return new AgentVersion(id, previous.agentId, previous.number.next(), source, snapshot, cost,
                Tier.initial(), PublicationState.DRAFT);
    }

    public static AgentVersion restore(
            AgentVersionId id,
            AgentId agentId,
            VersionNumber number,
            SourceWorkflow source,
            WorkflowGraph snapshot,
            VersionCost cost,
            Tier tier,
            PublicationState state
    ) {
        return new AgentVersion(id, agentId, number, source, snapshot, cost, tier, state);
    }

    private static void requireExecutable(WorkflowGraph snapshot) {
        if (snapshot.isEmpty()) {
            throw new CoreException(ErrorType.AGENT_WORKFLOW_EMPTY);
        }
    }

    public AgentVersion publish() {
        if (isPublished()) {
            throw new CoreException(ErrorType.AGENT_VERSION_ALREADY_PUBLISHED);
        }

        return new AgentVersion(id, agentId, number, source, snapshot, cost, tier, PublicationState.PUBLISHED);
    }

    public boolean isPublished() {
        return state == PublicationState.PUBLISHED;
    }

    public AbcAmount finalPrice() {
        return cost.baseCost().multiply(BigDecimal.ONE.add(tier.premiumRate()));
    }
}
