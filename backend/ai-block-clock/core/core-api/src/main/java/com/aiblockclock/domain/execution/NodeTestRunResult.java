package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeTestRun;

// 판매자에게 보여 줄 테스트 실행이다. baseCost 는 지금 노드의 원가로, 원가를 잰 테스트가 끝나면 새로 정한 값이 온다.
// artifact 는 성공한 테스트의 결과물(엔진이 씌운 봉투 JSON)이다. 엔진이 다시 올라가 실행을 잊었으면 비어 있다.
public record NodeTestRunResult(NodeTestRun run, AbcAmount baseCost, String artifact) {
}
