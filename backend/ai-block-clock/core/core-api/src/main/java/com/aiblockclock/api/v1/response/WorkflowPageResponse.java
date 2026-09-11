package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.workflow.WorkflowSummary;
import com.aiblockclock.support.paging.CursorPage;

import java.time.Instant;
import java.util.List;

public record WorkflowPageResponse(List<WorkflowItemResponse> content, String nextCursor, boolean hasNext) {
    public static WorkflowPageResponse from(CursorPage<WorkflowSummary> page) {
        List<WorkflowItemResponse> content = page.content().stream()
                .map(summary -> new WorkflowItemResponse(
                        summary.id().value().toString(),
                        summary.name(),
                        summary.lastExecutedAt(),
                        summary.updatedAt()))
                .toList();
        return new WorkflowPageResponse(content, page.nextCursor(), page.hasNext());
    }

    public record WorkflowItemResponse(
            String workflowId,
            String name,
            Instant lastExecutedAt,
            Instant updatedAt
    ) {
    }
}
