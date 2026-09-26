package com.aiblockclock.domain.node;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.node.NodeTestRunStatus;

import java.math.BigDecimal;
import java.time.Instant;

// 판매자가 노드 하나를 엔진에 실제로 돌려 본 기록이다. runId 는 엔진이 준 실행 번호로, 엔진에 넘기기 전에는 비어 있다.
// costUsd 는 엔진이 잰 달러 원가이고 끝나기 전에는 비어 있다. 모델을 부르지 않은 mock 테스트는 원가를 정할 때 쓰지 않는다.
// resultJson 은 닫을 때 한 번 받아 JSON 으로 읽어 본 결과물이다. 다시 볼 때 엔진을 부르지 않는다.
public record NodeTestRun(
        NodeTestRunId id, NodeDefinitionId nodeId, UserId requester, String runId, boolean mock,
        NodeTestRunStatus status, BigDecimal costUsd, String failureCode, String resultJson, Instant requestedAt,
        Instant finishedAt) {

    public boolean isRunning() {
        return status == NodeTestRunStatus.RUNNING;
    }

    public boolean isSucceeded() {
        return status == NodeTestRunStatus.SUCCEEDED;
    }

    public boolean isRequestedBy(UserId candidate) {
        return requester.equals(candidate);
    }

    public boolean isOf(NodeDefinitionId candidate) {
        return nodeId.equals(candidate);
    }

    // 모델을 실제로 불러 원가를 잰 성공 기록만 노드 원가에 넣는다.
    public boolean measuredCost() {
        return isSucceeded() && !mock && costUsd != null && costUsd.signum() > 0;
    }
}
