package com.aiblockclock.domain.workflow;

import org.springframework.stereotype.Component;

// 두 메서드 모두 아직 볼 곳이 없어 false 를 돌려준다.
// 그래서 WorkflowDeletionPolicy 의 WORKFLOW_009 · WORKFLOW_010 은 지금 한 번도 걸리지 않는다.
// 에이전트 버전과 실행 이력이 붙으면 그때 실제 조회로 바꾼다.
@Component
public class WorkflowUsageReader implements WorkflowUsageView {
    // 에이전트 버전이 이 워크플로우를 원본으로 쓰는지 — 에이전트 애그리거트 연동 전까지 false
    @Override
    public boolean isSourceOfAnyVersion(WorkflowId workflowId) {
        return false;
    }

    // 실행 중인 워크플로우인지 — 실행 이력 연동 전까지 false
    @Override
    public boolean isExecuting(WorkflowId workflowId) {
        return false;
    }
}
