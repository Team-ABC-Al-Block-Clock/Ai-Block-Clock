package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.workflow.Revision;
import com.aiblockclock.domain.workflow.WorkflowId;

public record SourceWorkflow(WorkflowId workflowId, Revision revision) {
}
