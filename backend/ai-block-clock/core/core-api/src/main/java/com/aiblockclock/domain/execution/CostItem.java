package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.workflow.SourceType;

import java.math.BigDecimal;

// 견적의 한 줄. 에이전트는 속을 펼치지 않고 판매가 하나로 적는다.
// priceMultiplier 는 원가에 곱한 배율(1 + 등급 할증)이다. 원가만 받는 자리는 1 이다.
// 끝난 뒤 실측 원가에도 같은 배율을 곱해 사용액을 센다.
public record CostItem(NodeKey nodeKey, SourceType sourceType, String name, AbcAmount price,
                       BigDecimal priceMultiplier) {
}
