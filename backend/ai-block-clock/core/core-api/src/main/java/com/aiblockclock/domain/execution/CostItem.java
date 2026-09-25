package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.workflow.SourceType;

// 견적의 한 줄. 에이전트는 속을 펼치지 않고 판매가 하나로 적는다.
public record CostItem(NodeKey nodeKey, SourceType sourceType, String name, AbcAmount price) {
}
