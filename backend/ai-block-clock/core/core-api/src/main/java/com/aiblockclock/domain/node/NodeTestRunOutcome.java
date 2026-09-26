package com.aiblockclock.domain.node;

import com.aiblockclock.enums.node.NodeTestRunStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;

// 엔진이 알린 테스트 실행의 끝이다. costUsd 는 모델을 부른 만큼이고, 부르기 전에 실패했으면 비어 있다.
public record NodeTestRunOutcome(NodeTestRunStatus status, BigDecimal costUsd, String failureCode) {
    // node_test_run.cost_usd 컬럼과 같은 자릿수다. 엔진도 달러를 소수 여섯째 자리까지 준다 (계약 8.1).
    private static final int DECIMAL_PLACES = 6;

    public NodeTestRunOutcome {
        costUsd = costUsd == null ? null : costUsd.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }
}
