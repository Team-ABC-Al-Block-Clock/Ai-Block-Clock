package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.Workflow;

import java.time.Instant;

public record WorkflowSaveResponse(
        String workflowId,
        String name,
        long revision,
        int nodeCount,
        int edgeCount,
        String estimatedCost,
        String currency,
        Instant createdAt
) {
    public static WorkflowSaveResponse from(Workflow workflow) {
        return new WorkflowSaveResponse(
                workflow.getId().value().toString(),
                workflow.getName(),
                workflow.getRevision().value(),
                workflow.getGraph().nodeCount(),
                workflow.getGraph().edgeCount(),
                WorkflowPricing.UNPRICED,
                WorkflowPricing.CURRENCY,
                workflow.getTimestamps().createdAt());
    }
}
