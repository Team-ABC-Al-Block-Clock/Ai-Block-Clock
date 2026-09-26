package com.aiblockclock.domain.workflow;

import org.springframework.stereotype.Component;

// 실행 이력이 붙기 전까지 false 를 돌려준다. 그래서 WORKFLOW_010 은 지금 한 번도 걸리지 않는다.
@Component
public class WorkflowUsageReader implements WorkflowUsageView {
    @Override
    public boolean isExecuting(WorkflowId workflowId) {
        return false;
    }
}
